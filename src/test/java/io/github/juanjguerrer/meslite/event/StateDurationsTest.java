package io.github.juanjguerrer.meslite.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.juanjguerrer.meslite.event.StateDurations.Result;
import io.github.juanjguerrer.meslite.event.StateDurations.StateChange;

class StateDurationsTest {

    private static final long HOUR_MS = Duration.ofHours(1).toMillis();

    private static final Instant FROM = at("08:00");
    private static final Instant TO = at("12:00");

    private static Instant at(String hhmm) {
        return Instant.parse("2026-10-20T" + hhmm + ":00Z");
    }

    private static StateChange change(MachineState state, String hhmm) {
        return new StateChange(state, at(hhmm));
    }

    private static void assertCoversWholeWindow(Result result, Instant from, Instant to) {
        long knownMs = result.durationsMs().values().stream().mapToLong(Long::longValue).sum();

        assertThat(knownMs + result.unknownMs()).isEqualTo(Duration.between(from, to).toMillis());
    }

    @Test
    void countsLeadingUnknownTimeWhenInitialStateIsNull() {
        List<StateChange> changes = List.of(
            change(MachineState.RUNNING, "09:00"),
            change(MachineState.DOWN, "11:00"));

        Result result = StateDurations.calculate(null, changes, FROM, TO);

        assertThat(result.unknownMs()).isEqualTo(HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(2 * HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.DOWN)).isEqualTo(HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.IDLE)).isZero();
        assertCoversWholeWindow(result, FROM, TO);
    }

    @Test
    void usesInitialStateUntilFirstChange() {
        List<StateChange> changes = List.of(
            change(MachineState.RUNNING, "09:00"),
            change(MachineState.DOWN, "11:00"));

        Result result = StateDurations.calculate(MachineState.IDLE, changes, FROM, TO);

        assertThat(result.unknownMs()).isZero();
        assertThat(result.durationsMs().get(MachineState.IDLE)).isEqualTo(HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(2 * HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.DOWN)).isEqualTo(HOUR_MS);
        assertCoversWholeWindow(result, FROM, TO);
    }

    @Test
    void withoutChangesTheWholeWindowBelongsToTheInitialState() {
        Result result = StateDurations.calculate(MachineState.SETUP, List.of(), FROM, TO);

        assertThat(result.durationsMs().get(MachineState.SETUP)).isEqualTo(4 * HOUR_MS);
        assertThat(result.unknownMs()).isZero();
        assertCoversWholeWindow(result, FROM, TO);
    }

    @Test
    void withoutChangesAndWithoutInitialStateTheWholeWindowIsUnknown() {
        Result result = StateDurations.calculate(null, List.of(), FROM, TO);

        assertThat(result.unknownMs()).isEqualTo(4 * HOUR_MS);
        assertThat(result.durationsMs().values()).allMatch(ms -> ms == 0L);
    }

    @Test
    void changeExactlyAtFromLeavesNoTimeForTheInitialState() {
        List<StateChange> changes = List.of(change(MachineState.RUNNING, "08:00"));

        Result result = StateDurations.calculate(MachineState.IDLE, changes, FROM, TO);

        assertThat(result.durationsMs().get(MachineState.IDLE)).isZero();
        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(4 * HOUR_MS);
        assertThat(result.unknownMs()).isZero();
    }

    @Test
    void sameStateAppearingTwiceAccumulates() {
        List<StateChange> changes = List.of(
            change(MachineState.DOWN, "09:00"),
            change(MachineState.RUNNING, "10:00"));

        Result result = StateDurations.calculate(MachineState.RUNNING, changes, FROM, TO);

        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(3 * HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.DOWN)).isEqualTo(HOUR_MS);
        assertCoversWholeWindow(result, FROM, TO);
    }

    @Test
    void changesAtTheSameInstantLetTheLastOneWin() {
        List<StateChange> changes = List.of(
            change(MachineState.SETUP, "09:00"),
            change(MachineState.RUNNING, "09:00"));

        Result result = StateDurations.calculate(MachineState.IDLE, changes, FROM, TO);

        assertThat(result.durationsMs().get(MachineState.SETUP)).isZero();
        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(3 * HOUR_MS);
        assertThat(result.durationsMs().get(MachineState.IDLE)).isEqualTo(HOUR_MS);
        assertCoversWholeWindow(result, FROM, TO);
    }

    @Test
    void everyStateAppearsInTheResultEvenWithZeroTime() {
        Result result = StateDurations.calculate(MachineState.RUNNING, List.of(), FROM, TO);

        assertThat(result.durationsMs()).containsOnlyKeys(MachineState.values());
    }

    @Test
    void rejectsWindowWhereFromIsNotBeforeTo() {
        assertThatThrownBy(() -> StateDurations.calculate(null, List.of(), TO, FROM))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateDurations.calculate(null, List.of(), FROM, FROM))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
