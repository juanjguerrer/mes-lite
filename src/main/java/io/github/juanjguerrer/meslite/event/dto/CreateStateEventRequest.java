package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.juanjguerrer.meslite.event.MachineState;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStateEventRequest(
    @NotNull UUID eventId,
    @NotNull MachineState state,
    @NotNull Instant occurredAt,
    @Size(max = 255) String reason
) {}
