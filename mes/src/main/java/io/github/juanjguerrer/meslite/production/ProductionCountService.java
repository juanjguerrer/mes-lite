package io.github.juanjguerrer.meslite.production;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.exception.ConflictException;
import io.github.juanjguerrer.meslite.common.exception.NotFoundException;
import io.github.juanjguerrer.meslite.common.query.TimeRanges;
import io.github.juanjguerrer.meslite.event.EventTimeRules;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;
import io.github.juanjguerrer.meslite.production.dto.ProductionCountResponse;
import io.github.juanjguerrer.meslite.production.dto.ProductionTotalsResponse;

@Service
public class ProductionCountService {
    private final ProductionCountRepository repository;
    private final MachineRepository machineRepository;
    private final Clock clock;

    public ProductionCountService(ProductionCountRepository repository, MachineRepository machineRepository, Clock clock) {
        this.repository = repository;
        this.machineRepository = machineRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProductionTotalsResponse totals(
        Long machineId,
        Instant from,
        Instant to
    ){
        if (!machineRepository.existsById(machineId)) {
            throw NotFoundException.byId("Machine", machineId);
        }
        var range = TimeRanges.validateAndAdjust(from, to, clock);
        var totals = repository.sumByMachineAndRange(machineId, range.from(), range.to());
        return new ProductionTotalsResponse(
            machineId, range.from(), range.to(),
            totals.getGood(), totals.getScrap(), totals.getGood() + totals.getScrap()
        );
    }

    public record IngestResult(ProductionCountResponse count, boolean created) { }

    // Not @Transactional on purpose: each repository call runs in its own transaction,
    // so after a unique violation on event_id we can still query the event that won the race.
    public IngestResult ingest(
        CreateProductionCountRequest request,
        String lineCode,
        String machineCode
    ) {
        String formattedLineCode = lineCode.trim().toUpperCase(Locale.ROOT);
        String formattedMachineCode = machineCode.trim().toUpperCase(Locale.ROOT);
        var machine = machineRepository.findByLineCodeAndCode(formattedLineCode, formattedMachineCode)
            .orElseThrow(() -> NotFoundException.byLineCodeAndMachineCode(formattedLineCode, formattedMachineCode));
        var from = request.intervalStart().truncatedTo(ChronoUnit.MICROS);
        var to = request.intervalEnd().truncatedTo(ChronoUnit.MICROS);
        if (!from.isBefore(to)) {
            throw new BusinessRuleException("intervalStart must be before intervalEnd");
        }
        EventTimeRules.validateOccurredAt(clock, to);
        var existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return replay(existing.get(), machine, request.goodCount(), request.scrapCount(), from, to);
        }
        var count = new ProductionCount(machine, request.eventId(), request.goodCount(), request.scrapCount(), from, to);
        try {
            return new IngestResult(ProductionCountResponse.from(repository.save(count)), true);
        } catch (DataIntegrityViolationException e) {
            var concurrentCount = repository.findByEventId(request.eventId())
            .orElseThrow(() -> e);
            return replay(concurrentCount, machine, request.goodCount(), request.scrapCount(), from, to);
        }
    }

    private IngestResult replay(ProductionCount existing, Machine machine,
        Long goodCount, Long scrapCount, Instant from, Instant to
    ) {
        if (!existing.describesSameFact(machine, goodCount, scrapCount, from, to, existing.getSequence())) {
            throw new ConflictException("eventId " + existing.getEventId() + " already exists with different data.");
        }
        return new IngestResult(ProductionCountResponse.from(existing), false);
    }

    public ProductionCountResponse findById(Long id) {
        return repository.findById(id)
            .map(ProductionCountResponse::from)
            .orElseThrow(() -> NotFoundException.byId("Production count", id));
    }
}
