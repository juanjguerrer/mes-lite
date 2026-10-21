package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.event.MachineState;

public record MachineCurrentStateResponse(
    Long machineId,
    String machineCode,
    MachineState state,
    Instant since
) { }
