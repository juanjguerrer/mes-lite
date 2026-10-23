package io.github.juanjguerrer.meslite.oee;

import java.util.ArrayList;
import java.util.List;

import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.StateDurations;

public final class OeeCalculator {

    /**
     * Factors are ratios between 0 and 1 (performance may exceed 1). A null factor means it cannot be computed.
     */
    public record OeeResult(
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
    ) { }

    private OeeCalculator() { }

    /**
     * @param durations        time per state in the range (from StateDurations)
     * @param idealCycleTimeMs ideal cycle time of the machine, or null if it has none
     */
    public static OeeResult calculate(StateDurations.Result durations, long goodCount, long scrapCount, Integer idealCycleTimeMs) {
        long rangeMs = durations.durationsMs().values().stream().mapToLong(Long::longValue).sum() + durations.unknownMs();
        long plannedStopMs = durations.durationsMs().getOrDefault(MachineState.PLANNED_STOP, 0L);
        long runningMs = durations.durationsMs().getOrDefault(MachineState.RUNNING, 0L);
        long plannedMs = rangeMs - plannedStopMs - durations.unknownMs();
        Double availability = plannedMs == 0 ? null : (double) runningMs / plannedMs;
        long totalCount = goodCount + scrapCount;
        Double performance = (idealCycleTimeMs == null || runningMs == 0) ? null : (double) totalCount  * idealCycleTimeMs / runningMs;
        Double quality = totalCount == 0 ? null : (double) goodCount / totalCount;
        Double oee = (availability == null || performance == null || quality == null) ? null : availability * performance * quality;
        double dataCoverage = rangeMs == 0 ? 1.0 : (double) (rangeMs - durations.unknownMs()) / rangeMs;
        ArrayList<String> warnings = new ArrayList<>();
        if (availability == null) warnings.add("availability cannot be computed (plannedMs = 0)");
        if (performance == null) warnings.add("performance cannot be computed (ideal cycle time is missing or runningMs = 0)");
        if (performance != null && performance > 1) warnings.add("performance exceeds 100%: check the ideal cycle time (it may be too long)");
        if (quality == null) warnings.add("quality cannot be computed (no pieces)");
        if (oee == null) warnings.add("oee cannot be computed (one or more factors are null)");
        return new OeeResult(
            plannedMs, runningMs, durations.unknownMs(),
            goodCount, scrapCount,
            availability, performance, quality, oee,
            dataCoverage,
            List.copyOf(warnings)
        );
    }
}
