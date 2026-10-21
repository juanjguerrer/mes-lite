package io.github.juanjguerrer.meslite.event;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import io.github.juanjguerrer.meslite.common.NotFoundException;
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.event.dto.StateEventResponse;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.common.ConflictException;

@Service
public class MachineStateEventService {
    private final MachineStateEventRepository repository;
    private final MachineRepository machineRepository;
    private final Clock clock;

    public MachineStateEventService(MachineStateEventRepository repository, MachineRepository machineRepository, Clock clock) {
        this.repository = repository;
        this.machineRepository = machineRepository;
        this.clock = clock;
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
}
