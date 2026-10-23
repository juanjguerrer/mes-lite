package io.github.juanjguerrer.meslite.shift.dto;

import java.time.Instant;
import java.time.LocalTime;

import io.github.juanjguerrer.meslite.shift.Shift;

public record ShiftResponse(
    Long id,
    Long lineId,
    String code,
    String name,
    LocalTime startTime,
    LocalTime endTime,
    boolean crossesMidnight,
    Instant createdAt,
    Instant updatedAt
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
