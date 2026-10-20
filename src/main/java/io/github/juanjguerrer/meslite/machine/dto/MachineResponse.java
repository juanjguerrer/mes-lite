package io.github.juanjguerrer.meslite.machine.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.machine.Machine;

public record MachineResponse(
    Long id,
    String name,
    String code,
    String description,
    Integer idealCycleTimeMs,
    Long lineId,
    Instant createdAt,
    Instant updatedAt
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
