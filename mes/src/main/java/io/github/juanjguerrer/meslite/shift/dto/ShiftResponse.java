package io.github.juanjguerrer.meslite.shift.dto;

import java.time.Instant;
import java.time.LocalTime;

import io.github.juanjguerrer.meslite.shift.Shift;
import io.swagger.v3.oas.annotations.media.Schema;

public record ShiftResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime startTime,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime endTime,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean crossesMidnight,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
            shift.getId(),
            shift.getLine().getId(),
            shift.getCode(),
            shift.getName(),
            shift.getStartTime(),
            shift.getEndTime(),
            !shift.getEndTime().isAfter(shift.getStartTime()),
            shift.getCreatedAt(),
            shift.getUpdatedAt()
        );
    }
}
