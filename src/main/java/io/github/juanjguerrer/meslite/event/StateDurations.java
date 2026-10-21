package io.github.juanjguerrer.meslite.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.time.Duration;

public final class StateDurations {

    public record StateChange(MachineState state, Instant occurredAt) { }

    public record Result(Map<MachineState, Long> durationsMs, long unknownMs) { }

    private StateDurations() { }

    /**
     * Milliseconds spent in each state within [from, to).
     *
     * @param initialState state of the last event before {@code from}, or null if unknown
     * @param changes      events with from <= occurredAt < to, sorted by occurredAt then id
     */
    public static Result calculate(MachineState initialState, List<StateChange> changes, Instant from, Instant to) {
        // example 9 to 12, from 9 to 10 is unknown, then one change at 10 to state A
        // check that from is before or equal to to
        if (from.isAfter(to) || from.equals(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        long unknownMs = 0;
        Map<MachineState, Long> durationsMs = new EnumMap<>(MachineState.class);
        for (MachineState state : MachineState.values()) {
            durationsMs.put(state, 0L);
        }

        MachineState current = initialState; // null
        Instant cursor = from; // 9

        for (StateChange change : changes) {
            addSegment(durationsMs, current, cursor, change.occurredAt());
            current = change.state(); // A
            cursor = change.occurredAt(); // 10
        }
        // Add the last segment
        addSegment(durationsMs, current, cursor, to);
        var totalDurationMs = Duration.between(from, to).toMillis();
        var knownDurationMs = durationsMs.values().stream().mapToLong(Long::longValue).sum();
        unknownMs = totalDurationMs - knownDurationMs;
        return new Result(durationsMs, unknownMs);
    }

    private static void addSegment(Map<MachineState, Long> durations,
        MachineState state,
        Instant start,
        Instant end
    ) {
        if (state == null) {
            return;
        }
        long durationMs = Duration.between(start, end).toMillis();
        durations.put(state, durations.get(state) + durationMs);
    }
}
