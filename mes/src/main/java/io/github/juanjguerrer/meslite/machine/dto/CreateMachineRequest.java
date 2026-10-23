package io.github.juanjguerrer.meslite.machine.dto;

import io.github.juanjguerrer.meslite.common.CycleTimeUnit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMachineRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String code,
    @Size(max = 20000) String description,
    Integer idealCycleTime,
    CycleTimeUnit idealCycleTimeUnit
) { }
