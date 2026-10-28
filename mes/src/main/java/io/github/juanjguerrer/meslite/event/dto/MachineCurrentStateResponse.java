package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.event.MachineState;
import io.swagger.v3.oas.annotations.media.Schema;

public record MachineCurrentStateResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long machineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String machineCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) MachineState state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant since
) { }
