package io.github.juanjguerrer.meslite.shift;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import io.github.juanjguerrer.meslite.shift.ShiftWindow.Window;

class ShiftWindowTest {

    // UTC-6 all year: Mexico dropped daylight saving time in 2022.
    private static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");
    // Has daylight saving time: in 2026 it starts on March 8 and ends on November 1, both at 02:00.
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);

    @Test
    void dayShiftStaysOnTheSameDate() {
        Window window = ShiftWindow.resolve(time("06:00"), time("14:00"), OCT_22, MEXICO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-22T20:00:00Z"));
    }

    @Test
    void shiftStartingAtMidnightStaysOnTheSameDate() {
        Window window = ShiftWindow.resolve(time("00:00"), time("08:00"), OCT_22, MEXICO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-22T06:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-22T14:00:00Z"));
    }

    @Test
    void nightShiftStartsOnTheGivenDateAndEndsTheNextDay() {
        Window window = ShiftWindow.resolve(time("22:00"), time("06:00"), OCT_22, MEXICO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-23T04:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-23T12:00:00Z"));
        assertThat(hours(window)).isEqualTo(8);
    }

    @Test
    void shiftEndingAtMidnightEndsTheNextDay() {
        Window window = ShiftWindow.resolve(time("14:00"), time("00:00"), OCT_22, MEXICO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-10-22T20:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-10-23T06:00:00Z"));
        assertThat(hours(window)).isEqualTo(10);
    }

    @Test
    void nightShiftIsNineHoursWhenClocksFallBack() {
        // 22:00 CDT (UTC-5) on Oct 31 -> 06:00 CST (UTC-6) on Nov 1: the 01:00-02:00 hour happens twice.
        Window window = ShiftWindow.resolve(time("22:00"), time("06:00"), LocalDate.of(2026, 10, 31), CHICAGO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
        assertThat(hours(window)).isEqualTo(9);
    }

    @Test
    void nightShiftIsSevenHoursWhenClocksSpringForward() {
        // 22:00 CST (UTC-6) on Mar 7 -> 06:00 CDT (UTC-5) on Mar 8: the 02:00-03:00 hour does not exist.
        Window window = ShiftWindow.resolve(time("22:00"), time("06:00"), LocalDate.of(2026, 3, 7), CHICAGO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-03-08T04:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-03-08T11:00:00Z"));
        assertThat(hours(window)).isEqualTo(7);
    }

    @Test
    void startInsideTheSpringForwardGapMovesOneHourLater() {
        // 02:30 does not exist on Mar 8 in Chicago; java.time shifts it to 03:30 CDT.
        Window window = ShiftWindow.resolve(time("02:30"), time("10:00"), LocalDate.of(2026, 3, 8), CHICAGO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-03-08T08:30:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-03-08T15:00:00Z"));
    }

    @Test
    void sameDayShiftIsUnaffectedByAnotherDaysTimeChange() {
        Window window = ShiftWindow.resolve(time("06:00"), time("14:00"), LocalDate.of(2026, 11, 1), CHICAGO);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
        assertThat(hours(window)).isEqualTo(8);
    }

    @Test
    void equalStartAndEndIsTreatedAsAFullDay() {
        // The database rejects it (ck_shifts_times_differ); this documents what the function does anyway.
        Window window = ShiftWindow.resolve(time("08:00"), time("08:00"), OCT_22, MEXICO);

        assertThat(hours(window)).isEqualTo(24);
    }

    private static LocalTime time(String value) {
        return LocalTime.parse(value);
    }

    private static long hours(Window window) {
        return Duration.between(window.from(), window.to()).toHours();
    }
}
