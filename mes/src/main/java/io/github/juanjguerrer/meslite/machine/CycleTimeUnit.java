package io.github.juanjguerrer.meslite.machine;

import java.time.temporal.ChronoUnit;

public enum CycleTimeUnit {
    MILLISECONDS(ChronoUnit.MILLIS),
    SECONDS(ChronoUnit.SECONDS),
    MINUTES(ChronoUnit.MINUTES),
    HOURS(ChronoUnit.HOURS);

    private final ChronoUnit chronoUnit;

    CycleTimeUnit(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    public ChronoUnit chronoUnit() {
        return chronoUnit;
    }
}