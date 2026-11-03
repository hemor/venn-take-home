package com.emmanuel.venn.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/** The UTC day and Monday-based week that contain an instant. */
public record LimitWindows(Instant dayStart, Instant dayEnd, Instant weekStart, Instant weekEnd) {

    public static LimitWindows containing(Instant instant) {
        LocalDate day = instant.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LimitWindows(
                startOf(day), startOf(day.plusDays(1)), startOf(monday), startOf(monday.plusWeeks(1)));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
