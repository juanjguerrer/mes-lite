package io.github.juanjguerrer.meslite.oee.dto;

import java.time.Instant;
import java.util.List;

import io.github.juanjguerrer.meslite.oee.OeeCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

public record OeeResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long machineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String shiftCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long plannedMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long runningMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unknownMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long goodCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long scrapCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double availability,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double performance,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double quality,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double oee,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double dataCoverage,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> warnings
) {
    public static OeeResponse from(Long machineId, Instant from, Instant to, String shiftCode, OeeCalculator.OeeResult result) {
        return new OeeResponse(
            machineId, from, to, shiftCode,
            result.plannedMs(), result.runningMs(), result.unknownMs(),
            result.goodCount(), result.scrapCount(),
            round4(result.availability()),
            round4(result.performance()),
            round4(result.quality()),
            round4(result.oee()),
            result.dataCoverage(),
            result.warnings()
        );
    }

    private static Double round4(Double value) {
        return value == null ? null : Math.round(value * 10_000) / 10_000.0;
    }
}
