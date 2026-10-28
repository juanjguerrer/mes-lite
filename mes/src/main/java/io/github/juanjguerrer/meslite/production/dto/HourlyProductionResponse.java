package io.github.juanjguerrer.meslite.production.dto;

import java.time.Instant;
import java.util.List;

public record HourlyProductionResponse(
    Long machineId, Instant from, Instant to,
    List<Bucket> buckets
) {
    public record Bucket(Instant bucketStart, long goodCount, long scrapCount) { }
}
