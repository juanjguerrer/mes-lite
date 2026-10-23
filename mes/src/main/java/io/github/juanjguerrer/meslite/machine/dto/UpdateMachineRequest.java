package io.github.juanjguerrer.meslite.machine.dto;

import io.github.juanjguerrer.meslite.common.CycleTimeUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMachineRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 20000) String description,
    Integer idealCycleTime,
    CycleTimeUnit idealCycleTimeUnit
) { }
