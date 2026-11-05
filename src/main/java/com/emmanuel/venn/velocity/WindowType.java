package com.emmanuel.venn.velocity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public enum WindowType {
    DAY {
        @Override
        public TimeRange rangeContaining(Instant instant) {
            LocalDate day = dateOf(instant);
            return new TimeRange(startOf(day), startOf(day.plusDays(1)));
        }
    },
    WEEK {
        @Override
        public TimeRange rangeContaining(Instant instant) {
            LocalDate monday = dateOf(instant).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new TimeRange(startOf(monday), startOf(monday.plusWeeks(1)));
        }
    };

    /** The UTC calendar range of this type that contains the instant. */
    public abstract TimeRange rangeContaining(Instant instant);

    private static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
