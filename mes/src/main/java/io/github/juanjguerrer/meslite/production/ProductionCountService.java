package io.github.juanjguerrer.meslite.production;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.config.PlantProperties;
import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.exception.ConflictException;
import io.github.juanjguerrer.meslite.common.exception.NotFoundException;
import io.github.juanjguerrer.meslite.common.query.TimeRanges;
import io.github.juanjguerrer.meslite.event.EventTimeRules;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;
import io.github.juanjguerrer.meslite.production.dto.HourlyProductionResponse;
import io.github.juanjguerrer.meslite.production.dto.ProductionCountResponse;
import io.github.juanjguerrer.meslite.production.dto.ProductionTotalsResponse;

@Service
public class ProductionCountService {
    private final ProductionCountRepository repository;
    private final MachineRepository machineRepository;
    private final Clock clock;
    private final PlantProperties plantProperties;

    public ProductionCountService(
        ProductionCountRepository repository,
        MachineRepository machineRepository,
        Clock clock,
        PlantProperties plantProperties
    ) {
        this.repository = repository;
        this.machineRepository = machineRepository;
        this.clock = clock;
        this.plantProperties = plantProperties;
    }

    @Transactional(readOnly = true)
    public HourlyProductionResponse hourly(
        Long machineId,
        Instant from,
        Instant to
    ) {
        if (!machineRepository.existsById(machineId)) {
            throw NotFoundException.byId("Machine", machineId);
        }
        var range = TimeRanges.validateAndAdjust(from, to, clock);
        var buckets = repository
            .sumHourlyByMachineAndRange(machineId, range.from(), range.to(), plantProperties.zoneId().getId())
            .stream()
            .map(row -> new HourlyProductionResponse.Bucket(row.getBucketStart(), row.getGoodCount(), row.getScrapCount()))
            .toList();
        return new HourlyProductionResponse(machineId, range.from(), range.to(), buckets);
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
        String lineCode,
        String machineCode,
        CreateProductionCountRequest request
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
            return replay(existing.get(), machine, request.goodCount(), request.scrapCount(), from, to, request.sequence());
        }
        var existsByMachineIdAndSeq = repository.existsByMachineIdAndSequence(machine.getId(), request.sequence());
        if (existsByMachineIdAndSeq) {
            throw new ConflictException("Sequence " + request.sequence() + " was already used for machine " + machine.getCode());
        }
        var count = new ProductionCount(machine, request.eventId(), request.goodCount(), request.scrapCount(), from, to, request.sequence());
        try {
            return new IngestResult(ProductionCountResponse.from(repository.save(count)), true);
        } catch (DataIntegrityViolationException e) {
            var concurrentCount = repository.findByEventId(request.eventId())
            .orElseThrow(() -> toConflictOrOriginal(e, machine, request.sequence()));
            return replay(concurrentCount, machine, request.goodCount(), request.scrapCount(), from, to, request.sequence());
        }
    }

    private RuntimeException toConflictOrOriginal(DataIntegrityViolationException e, Machine machine, Long sequence)
    {
        for (Throwable cause = e; cause != null; cause = cause.getCause())
        {
            if (cause instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().toLowerCase(Locale.ROOT)
                    .contains("uq_production_counts_machine_id_sequence")) {
                return new ConflictException("Sequence " + sequence + " was already used for machine " + machine.getCode());
            }
        }
        return e;
    }

    private IngestResult replay(ProductionCount existing, Machine machine,
        Long goodCount, Long scrapCount, Instant from, Instant to, Long sequence
    ) {
        if (!existing.describesSameFact(machine, goodCount, scrapCount, from, to, sequence)) {
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
