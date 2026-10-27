package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.MachineStateEvent;

public record StateEventResponse(
    Long id,
    Long machineId,
    UUID eventId,
    MachineState state,
    Instant occurredAt,
    Instant receivedAt,
    String reason,
    Long sequence
) {
    public static StateEventResponse from(MachineStateEvent event) {
        return new StateEventResponse(
            event.getId(),
            event.getMachine().getId(),
            event.getEventId(),
            event.getState(),
            event.getOccurredAt(),
            event.getReceivedAt(),
            event.getReason(),
            event.getSequence()
        );
    }
}
