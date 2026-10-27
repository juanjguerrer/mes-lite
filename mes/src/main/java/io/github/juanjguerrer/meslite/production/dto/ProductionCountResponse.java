package io.github.juanjguerrer.meslite.production.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.juanjguerrer.meslite.production.ProductionCount;

public record ProductionCountResponse(
    Long id,
    Long machineId,
    UUID eventId,
    long goodCount,
    long scrapCount,
    Instant intervalStart,
    Instant intervalEnd,
    Instant receivedAt,
    Long sequence
) {
    public static ProductionCountResponse from(ProductionCount count) {
        return new ProductionCountResponse(
            count.getId(),
            count.getMachine().getId(),
            count.getEventId(),
            count.getGoodCount(),
            count.getScrapCount(),
            count.getIntervalStart(),
            count.getIntervalEnd(),
            count.getReceivedAt(),
            count.getSequence()
        );
    }
}
