package com.emmanuel.venn.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WindowTypeTest {

    private static TimeRange range(Instant start, Instant end) {
        return new TimeRange(start, end);
    }

    @Test
    void dayRunsFromMidnightUtcToNextMidnight() {
        assertThat(WindowType.DAY.rangeContaining(Instant.parse("2018-01-03T13:45:10Z")))
                .isEqualTo(range(Instant.parse("2018-01-03T00:00:00Z"), Instant.parse("2018-01-04T00:00:00Z")));
    }

    @Test
    void lastSecondOfDayBelongsToThatDay() {
        assertThat(WindowType.DAY.rangeContaining(Instant.parse("2018-01-03T23:59:59Z")).start())
                .isEqualTo(Instant.parse("2018-01-03T00:00:00Z"));
    }

    @Test
    void midnightStartsANewDay() {
        assertThat(WindowType.DAY.rangeContaining(Instant.parse("2018-01-04T00:00:00Z")).start())
                .isEqualTo(Instant.parse("2018-01-04T00:00:00Z"));
    }

    @Test
    void weekRunsFromMondayToNextMonday() {
        assertThat(WindowType.WEEK.rangeContaining(Instant.parse("2018-01-03T13:45:10Z")))
                .isEqualTo(range(Instant.parse("2018-01-01T00:00:00Z"), Instant.parse("2018-01-08T00:00:00Z")));
    }

    @Test
    void mondayMidnightIsInTheNewWeek() {
        assertThat(WindowType.WEEK.rangeContaining(Instant.parse("2018-01-08T00:00:00Z")).start())
                .isEqualTo(Instant.parse("2018-01-08T00:00:00Z"));
    }

    @Test
    void sundayLastSecondIsStillInThePreviousWeek() {
        assertThat(WindowType.WEEK.rangeContaining(Instant.parse("2018-01-07T23:59:59Z")))
                .isEqualTo(range(Instant.parse("2018-01-01T00:00:00Z"), Instant.parse("2018-01-08T00:00:00Z")));
    }

    @Test
    void weekCanSpanMonthAndYearBoundary() {
        assertThat(WindowType.WEEK.rangeContaining(Instant.parse("2019-01-01T10:00:00Z")).start())
                .isEqualTo(Instant.parse("2018-12-31T00:00:00Z"));
    }

    @Test
    void aRangeIncludesItsStartAndEndsWhereTheNextOneBegins() {
        TimeRange range = WindowType.DAY.rangeContaining(Instant.parse("2018-01-03T12:00:00Z"));

        assertThat(WindowType.DAY.rangeContaining(range.start())).isEqualTo(range);
        assertThat(WindowType.DAY.rangeContaining(range.end().minusSeconds(1))).isEqualTo(range);
        assertThat(WindowType.DAY.rangeContaining(range.end()).start()).isEqualTo(range.end());
        assertThat(WindowType.DAY.rangeContaining(range.start().minusSeconds(1)).end()).isEqualTo(range.start());
    }
}
