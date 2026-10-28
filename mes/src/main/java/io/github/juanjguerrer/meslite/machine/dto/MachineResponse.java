package io.github.juanjguerrer.meslite.machine.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.machine.Machine;
import io.swagger.v3.oas.annotations.media.Schema;

public record MachineResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer idealCycleTimeMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static MachineResponse from(Machine machine) {
        return new MachineResponse(
            machine.getId(),
            machine.getName(),
            machine.getCode(),
            machine.getDescription(),
            machine.getIdealCycleTimeMs(),
            machine.getLine().getId(),
            machine.getCreatedAt(),
            machine.getUpdatedAt()
        );
    }
}
