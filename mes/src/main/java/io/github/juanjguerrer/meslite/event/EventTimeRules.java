package io.github.juanjguerrer.meslite.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;

public final class EventTimeRules {
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private EventTimeRules() {
        // Private constructor to prevent instantiation
    }

    public static void validateOccurredAt(Clock clock, Instant occurredAt) {
        if (occurredAt == null) {
            throw new BusinessRuleException("OccurredAt timestamp cannot be null.");
        }

        Instant latestAllowed = clock.instant().plus(FUTURE_TOLERANCE);
        if (occurredAt.isAfter(latestAllowed)) {
            throw new BusinessRuleException("occurredAt cannot be more than " + FUTURE_TOLERANCE.toMinutes() + " minutes in the future .");
        }
    }
}
