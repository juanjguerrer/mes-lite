package io.github.juanjguerrer.meslite.common.query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;

public final class TimeRanges {
    private static final Duration MAX_DURATION = Duration.ofDays(31);

    public record Range(Instant from, Instant to) { }

    private TimeRanges() { }

    /**
     * Truncates both ends to microseconds (PostgreSQL precision), clips {@code to} to now
     * and rejects ranges longer than 31 days or not strictly increasing.
     */
    public static Range validateAndAdjust(Instant from, Instant to, Clock clock) {
        from = from.truncatedTo(ChronoUnit.MICROS);
        to = to.truncatedTo(ChronoUnit.MICROS);
        if (Duration.between(from, to).compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException("Invalid time range: 'from' and 'to' timestamps must be within 31 days.");
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (to.isAfter(now)) {
            to = now;
        }
        if (!from.isBefore(to)) {
            throw new BusinessRuleException("Invalid time range: 'from' timestamp must be before 'to' timestamp.");
        }
        return new Range(from, to);
    }
}
