package io.github.juanjguerrer.meslite.production.dto;

import java.time.Instant;

public record ProductionTotalsResponse(
    Long machineId, Instant from, Instant to,
    long goodCount, long scrapCount, long totalCount
) {

}
