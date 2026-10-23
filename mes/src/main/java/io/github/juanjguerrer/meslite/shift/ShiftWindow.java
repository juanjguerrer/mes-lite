package io.github.juanjguerrer.meslite.shift;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class ShiftWindow {
    public record Window(Instant from, Instant to) { }

    private ShiftWindow() { }

    public static Window resolve(
        LocalTime start,
        LocalTime end,
        LocalDate date,
        ZoneId zoneId
    ) {
        LocalDate endDate = date;
        if (!end.isAfter(start)) {
            endDate = date.plusDays(1);
        }
        Instant from = date.atTime(start).atZone(zoneId).toInstant();
        Instant to = endDate.atTime(end).atZone(zoneId).toInstant();


        return new Window(from, to);
    }
}
