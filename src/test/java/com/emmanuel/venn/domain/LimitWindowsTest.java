package com.emmanuel.venn.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LimitWindowsTest {

    @Test
    void dayRunsFromMidnightUtcToNextMidnight() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-03T13:45:10Z"));

        assertThat(windows.dayStart()).isEqualTo(Instant.parse("2018-01-03T00:00:00Z"));
        assertThat(windows.dayEnd()).isEqualTo(Instant.parse("2018-01-04T00:00:00Z"));
    }

    @Test
    void lastSecondOfDayBelongsToThatDay() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-03T23:59:59Z"));

        assertThat(windows.dayStart()).isEqualTo(Instant.parse("2018-01-03T00:00:00Z"));
    }

    @Test
    void midnightStartsANewDay() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-04T00:00:00Z"));

        assertThat(windows.dayStart()).isEqualTo(Instant.parse("2018-01-04T00:00:00Z"));
    }

    @Test
    void weekStartsOnMonday() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-03T13:45:10Z"));

        assertThat(windows.weekStart()).isEqualTo(Instant.parse("2018-01-01T00:00:00Z"));
        assertThat(windows.weekEnd()).isEqualTo(Instant.parse("2018-01-08T00:00:00Z"));
    }

    @Test
    void mondayMidnightIsInTheNewWeek() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-08T00:00:00Z"));

        assertThat(windows.weekStart()).isEqualTo(Instant.parse("2018-01-08T00:00:00Z"));
    }

    @Test
    void sundayLastSecondIsStillInThePreviousWeek() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2018-01-07T23:59:59Z"));

        assertThat(windows.weekStart()).isEqualTo(Instant.parse("2018-01-01T00:00:00Z"));
        assertThat(windows.weekEnd()).isEqualTo(Instant.parse("2018-01-08T00:00:00Z"));
    }

    @Test
    void weekCanSpanMonthAndYearBoundary() {
        LimitWindows windows = LimitWindows.containing(Instant.parse("2019-01-01T10:00:00Z"));

        assertThat(windows.weekStart()).isEqualTo(Instant.parse("2018-12-31T00:00:00Z"));
    }
}
