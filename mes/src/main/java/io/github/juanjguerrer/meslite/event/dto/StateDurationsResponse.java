package io.github.juanjguerrer.meslite.event.dto;

import java.time.Instant;
import java.util.Map;

import io.github.juanjguerrer.meslite.event.MachineState;
import io.swagger.v3.oas.annotations.media.Schema;

public record StateDurationsResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long machineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<MachineState, Long> durationsMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unknownMs
) { }
