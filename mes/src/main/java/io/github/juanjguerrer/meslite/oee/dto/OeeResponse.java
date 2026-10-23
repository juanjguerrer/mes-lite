package io.github.juanjguerrer.meslite.oee.dto;

import java.time.Instant;
import java.util.List;

import io.github.juanjguerrer.meslite.oee.OeeCalculator;

public record OeeResponse(
    Long machineId,
    Instant from,
    Instant to,
    String shiftCode,
    long plannedMs,
    long runningMs,
    long unknownMs,
    long goodCount,
    long scrapCount,
    Double availability,
    Double performance,
    Double quality,
    Double oee,
    double dataCoverage,
    List<String> warnings
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
