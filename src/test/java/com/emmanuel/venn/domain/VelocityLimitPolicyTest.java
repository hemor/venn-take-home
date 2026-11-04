package com.emmanuel.venn.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VelocityLimitPolicyTest {

    private static final Instant WEDNESDAY = Instant.parse("2018-01-03T12:00:00Z");
    private static final TimeRange DAY = WindowType.DAY.rangeContaining(WEDNESDAY);

    private static Totals totals(String amount, int count) {
        return new Totals(new BigDecimal(amount), count);
    }

    private static Optional<String> evaluate(List<VelocityRule> rules, String amount, Totals day, Totals week) {
        ActivityLookup lookup = range -> range.equals(DAY) ? day : week;
        return new VelocityLimitPolicy(rules).evaluate(new BigDecimal(amount), WEDNESDAY, lookup);
    }

    private static Optional<String> evaluateTaskRules(String amount, Totals day, Totals week) {
        return evaluate(TaskRules.rules(), amount, day, week);
    }

    @Test
    void acceptsFirstLoadWithNoPriorActivity() {
        assertThat(evaluateTaskRules("100.00", Totals.NONE, Totals.NONE)).isEmpty();
    }

    @Test
    void acceptsDailyTotalExactlyAtLimit() {
        assertThat(evaluateTaskRules("2000.00", totals("3000.00", 1), totals("3000.00", 1))).isEmpty();
    }

    @Test
    void rejectsDailyTotalOneCentOverLimit() {
        assertThat(evaluateTaskRules("2000.01", totals("3000.00", 1), totals("3000.00", 1)))
                .contains("DAILY_AMOUNT_LIMIT");
    }

    @Test
    void rejectsSingleLoadOverDailyLimit() {
        assertThat(evaluateTaskRules("5862.58", Totals.NONE, Totals.NONE)).contains("DAILY_AMOUNT_LIMIT");
    }

    @Test
    void acceptsWeeklyTotalExactlyAtLimit() {
        assertThat(evaluateTaskRules("5000.00", Totals.NONE, totals("15000.00", 0))).isEmpty();
    }

    @Test
    void rejectsWeeklyTotalOneCentOverLimit() {
        assertThat(evaluateTaskRules("1.00", Totals.NONE, totals("20000.00", 0))).contains("WEEKLY_AMOUNT_LIMIT");
    }

    @Test
    void acceptsThirdLoadOfTheDay() {
        assertThat(evaluateTaskRules("1.00", totals("2.00", 2), totals("2.00", 2))).isEmpty();
    }

    @Test
    void rejectsFourthLoadOfTheDayRegardlessOfAmount() {
        assertThat(evaluateTaskRules("1.00", totals("3.00", 3), totals("3.00", 3))).contains("DAILY_COUNT_LIMIT");
    }

    @Test
    void reportsTheFirstConfiguredRuleWhenSeveralAreBroken() {
        assertThat(evaluateTaskRules("6000.00", totals("0", 3), totals("20000.00", 3)))
                .contains("DAILY_COUNT_LIMIT");
    }

    @Test
    void acceptsEverythingWhenNoRulesAreConfigured() {
        assertThat(evaluate(List.of(), "999999.00", Totals.NONE, Totals.NONE)).isEmpty();
    }

    @Test
    void supportsRulesBeyondTheTaskLimits() {
        List<VelocityRule> rules = List.of(
                new VelocityRule("WEEKLY_COUNT_LIMIT", WindowType.WEEK, Metric.COUNT, new BigDecimal("2")));

        assertThat(evaluate(rules, "1.00", Totals.NONE, totals("2.00", 2))).contains("WEEKLY_COUNT_LIMIT");
        assertThat(evaluate(rules, "1.00", Totals.NONE, totals("1.00", 1))).isEmpty();
    }

    @Test
    void asksForActivityOncePerDistinctWindowOfTheAttempt() {
        List<TimeRange> asked = new ArrayList<>();
        ActivityLookup lookup = range -> {
            asked.add(range);
            return Totals.NONE;
        };

        new VelocityLimitPolicy(TaskRules.rules()).evaluate(new BigDecimal("1.00"), WEDNESDAY, lookup);

        assertThat(asked).containsExactlyInAnyOrder(DAY, WindowType.WEEK.rangeContaining(WEDNESDAY));
    }

    @Test
    void rejectsDuplicateRuleNames() {
        VelocityRule rule = TaskRules.rules().get(0);

        assertThatThrownBy(() -> new VelocityLimitPolicy(List.of(rule, rule)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }
}
