package io.github.juanjguerrer.meslite.event.dto;

import java.util.List;

public record SequenceGapsResponse(
    Long machineId,
    long totalMissing,
    List<Gap> gaps
) {
    public record Gap(
        Long fromSequence,
        Long toSequence,
        Long missing
    ) {}
}
