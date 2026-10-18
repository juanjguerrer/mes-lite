package io.github.juanjguerrer.meslite.common;

import ch.qos.logback.core.util.Duration;
import jakarta.annotation.Nullable;

public final class CycleTimeRules {
    private static final int MAX_CYCLE_TIME_MS = 24 * 60 * 60 * 1000;

    private CycleTimeRules() {
        // Private constructor to prevent instantiation
    }

    public static @Nullable Integer toMillis(Integer cycleTimeMs, CycleTimeUnit unit) {
        if (cycleTimeMs == null) {
            return null;
        }
        if (unit == null) {
            throw new BusinessRuleException("Cycle time unit cannot be null");
        }
        if (cycleTimeMs <= 0) {
            throw new BusinessRuleException("Cycle time must be a positive integer");
        }
        var result = Duration.of(value, unit.chronoUnit()).toMillis();
        if (result > MAX_CYCLE_TIME_MS) {
            throw new BusinessRuleException("Cycle time cannot exceed " + MAX_CYCLE_TIME_MS + " milliseconds");
        }
        return result;
    }
}
