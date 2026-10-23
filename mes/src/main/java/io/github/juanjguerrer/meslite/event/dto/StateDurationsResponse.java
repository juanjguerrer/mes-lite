package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;
import java.util.Map;

import io.github.juanjguerrer.meslite.event.MachineState;

public record StateDurationsResponse(
    Long machineId,
    Instant from,
    Instant to,
    Map<MachineState, Long> durationsMs,
    long unknownMs
) { }
