package io.github.juanjguerrer.meslite.shift;

import java.time.LocalTime;

public final class ShiftRules {
    private ShiftRules() {
        // Private constructor to prevent instantiation
    }

    public static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        if (!end1.isAfter(start1)) {
            boolean late = overlaps(start1, LocalTime.MAX, start2, end2);
            boolean early = end1.isAfter(LocalTime.MIN) && overlaps(LocalTime.MIN, end1, start2, end2);
            return late || early;
        }
        if (!end2.isAfter(start2)) {
            return overlaps(start1, end1, start2, LocalTime.MAX) || overlaps(start1, end1, LocalTime.MIN, end2);
        }
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
