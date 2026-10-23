package io.github.juanjguerrer.meslite.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.ConflictException;
import io.github.juanjguerrer.meslite.common.NotFoundException;
import io.github.juanjguerrer.meslite.common.TimeRanges;
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.event.dto.StateEventResponse;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.event.dto.MachineCurrentStateResponse;
import io.github.juanjguerrer.meslite.event.dto.StateDurationsResponse;

@Service
public class MachineStateEventService {
    private final MachineStateEventRepository repository;
    private final MachineRepository machineRepository;
    private final ProductionLineRepository lineRepository;
    private final Clock clock;

    public MachineStateEventService(
        MachineStateEventRepository repository,
        MachineRepository machineRepository,
        Clock clock, ProductionLineRepository
        lineRepository
    ) {
        this.repository = repository;
        this.machineRepository = machineRepository;
        this.clock = clock;
        this.lineRepository = lineRepository;
    }

    public record IngestResult(StateEventResponse event, boolean created) { }

    // Not @Transactional on purpose: each repository call runs in its own transaction,
    // so after a unique violation on event_id we can still query the event that won the race.
    public IngestResult ingest(String lineCode, String machineCode, CreateStateEventRequest request) {
        String formattedLineCode = lineCode.trim().toUpperCase(Locale.ROOT);
        String formattedMachineCode = machineCode.trim().toUpperCase(Locale.ROOT);
        var machine = machineRepository.findByLineCodeAndCode(formattedLineCode, formattedMachineCode)
            .orElseThrow(() -> NotFoundException.byLineCodeAndMachineCode(formattedLineCode, formattedMachineCode));
        Instant occurredAt = request.occurredAt().truncatedTo(ChronoUnit.MICROS);
        EventTimeRules.validateOccurredAt(clock, occurredAt);
        var existingEvent = repository.findByEventId(request.eventId());
        if (existingEvent.isPresent()) {
            return replay(existingEvent.get(), machine, request.state(), occurredAt);
        }
        var event = new MachineStateEvent(machine, request.eventId(), request.state(), occurredAt, request.reason());
        try {
            return new IngestResult(StateEventResponse.from(repository.save(event)), true);
        } catch (DataIntegrityViolationException e) {
            var concurrentEvent = repository.findByEventId(request.eventId())
            .orElseThrow(() -> e);
            return replay(concurrentEvent, machine, request.state(), occurredAt);
        }
    }

    @Transactional(readOnly = true)
    public Page<StateEventResponse> history(
        Long machineId,
        Instant from,
        Instant to,
        Pageable pageable
    ) {
        if (!machineRepository.existsById(machineId)) {
            throw NotFoundException.byId("Machine", machineId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("Invalid time range: 'from' timestamp must be before 'to' timestamp.");
        }

        List<Specification<MachineStateEvent>> specs = new ArrayList<>();
        if (from != null) {
            from = from.truncatedTo(ChronoUnit.MICROS);
            specs.add(MachineStateEventSpecifications.occurredAtFrom(from));
        }
        if (to != null) {
            to = to.truncatedTo(ChronoUnit.MICROS);
            specs.add(MachineStateEventSpecifications.occurredAtBefore(to));
        }
        specs.add(MachineStateEventSpecifications.machineEquals(machineId));

        Specification<MachineStateEvent> spec = Specification.allOf(specs);

        return repository.findAll(spec, pageable).map(StateEventResponse::from);
    }

    @Transactional(readOnly = true)
    public MachineCurrentStateResponse currentState(Long machineId) {
        var machine = machineRepository.findById(machineId)
            .orElseThrow(() -> NotFoundException.byId("Machine", machineId));
        var lastEvent = repository.findFirstByMachineIdOrderByOccurredAtDescIdDesc(machineId);
        return new MachineCurrentStateResponse(
            machine.getId(),
            machine.getCode(),
            lastEvent.map(MachineStateEvent::getState).orElse(null),
            lastEvent.map(MachineStateEvent::getOccurredAt).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<MachineCurrentStateResponse> currentStatesByLine(Long lineId) {
        if (!lineRepository.existsById(lineId)) {
            throw NotFoundException.byId("Production line", lineId);
        }

        List<MachineStateRow> rows = repository.findLatestByLineId(lineId);
        return rows.stream()
            .map(this::toResponse)
            .toList();
    }

    public StateEventResponse findById(Long id) {
        return repository.findById(id)
            .map(StateEventResponse::from)
            .orElseThrow(() -> NotFoundException.byId("Machine state event", id));
    }

    private IngestResult replay(MachineStateEvent existing, Machine machine, MachineState state, Instant occurredAt) {
        if (!existing.describesSameFact(machine, state, occurredAt)) {
            throw new ConflictException("eventId " + existing.getEventId() + " was already used for a different event");
        }
        return new IngestResult(StateEventResponse.from(existing), false);
    }

    private MachineCurrentStateResponse toResponse(MachineStateRow row) {
        MachineState state = row.getState() == null ? null : MachineState.valueOf(row.getState());
        return new MachineCurrentStateResponse(
            row.getMachineId(),
            row.getMachineCode(),
            state,
            row.getSince()
        );
    }

    @Transactional(readOnly = true)
    public StateDurationsResponse stateDurations(Long machineId, Instant from, Instant to) {
        if (!machineRepository.existsById(machineId)) {
            throw NotFoundException.byId("Machine", machineId);
        }
        var range = TimeRanges.validateAndAdjust(from, to, clock);
        from = range.from();
        to = range.to();
        var initialState = repository.findFirstByMachineIdAndOccurredAtBeforeOrderByOccurredAtDescIdDesc(machineId, from)
            .map(MachineStateEvent::getState)
            .orElse(null);

        Specification<MachineStateEvent> spec = Specification
            .allOf(
                MachineStateEventSpecifications.machineEquals(machineId),
                MachineStateEventSpecifications.occurredAtFrom(from),
                MachineStateEventSpecifications.occurredAtBefore(to)
            );

        var changes = repository.findAll(spec, Sort.by(MachineStateEvent::getOccurredAt, MachineStateEvent::getId))
            .stream()
            .map(e -> new StateDurations.StateChange(e.getState(), e.getOccurredAt()))
            .toList();

        var durations = StateDurations.calculate(initialState, changes, from, to);
        return new StateDurationsResponse(
            machineId,
            from,
            to,
            durations.durationsMs(),
            durations.unknownMs()
        );
    }

    public StateDurationsResponse stateDurationsWithSQL(
        Long machineId,
        Instant from,
        Instant to
    ) {
        if (!machineRepository.existsById(machineId)) {
            throw NotFoundException.byId("Machine", machineId);
        }
        var range = TimeRanges.validateAndAdjust(from, to, clock);
        from = range.from();
        to = range.to();

        var stateDurations = repository.sumDurationsByState(machineId, from, to);
        var totalDurationMs = Duration.between(from, to).toMillis();
        Map<MachineState, Long> durationsMs = new EnumMap<>(MachineState.class);
        for (MachineState state : MachineState.values()) {
            durationsMs.put(state, 0L);
        }
        var knownDurationMs = stateDurations.stream()
            .mapToLong(StateDurationRow::getDurationMs)
            .sum();
        var unknownMs = totalDurationMs - knownDurationMs;
        for (StateDurationRow row : stateDurations) {
            MachineState state = MachineState.valueOf(row.getState());
            durationsMs.put(state, row.getDurationMs());
        }
        return new StateDurationsResponse(
            machineId,
            from,
            to,
            durationsMs,
            unknownMs
        );
    }
}
