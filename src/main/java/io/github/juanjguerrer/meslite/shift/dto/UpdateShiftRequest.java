package io.github.juanjguerrer.meslite.shift.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateShiftRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
) { }
