package io.github.juanjguerrer.meslite.machine;

import java.time.Duration;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;

public final class CycleTimeRules {
    private static final long MAX_CYCLE_TIME_MS = 24 * 60 * 60 * 1000;

    private CycleTimeRules() {
        // Private constructor to prevent instantiation
    }

    public static Integer toMillis(Integer value, CycleTimeUnit unit) {
        if (value == null) {
            return null;
        }
        if (unit == null) {
            throw new BusinessRuleException("Cycle time unit cannot be null");
        }
        if (value <= 0) {
            throw new BusinessRuleException("Cycle time must be a positive integer");
        }
        var result = Duration.of(value, unit.chronoUnit()).toMillis();
        if (result > MAX_CYCLE_TIME_MS) {
            throw new BusinessRuleException("Cycle time cannot exceed " + MAX_CYCLE_TIME_MS + " milliseconds");
        }
        return Math.toIntExact(result);
    }
}
