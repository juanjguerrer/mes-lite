package io.github.juanjguerrer.meslite.production.dto;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record HourlyProductionResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long machineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Bucket> buckets
) {
    public record Bucket(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant bucketStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long goodCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long scrapCount) { }
}
