package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.CAD;
import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VelocityLimitPolicyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant WEDNESDAY = Instant.parse("2018-01-03T12:00:00Z");
    private static final TimeRange DAY = WindowType.DAY.rangeContaining(WEDNESDAY);

    private static Totals totals(String amount, int count) {
        return new Totals(cad(amount), count);
    }

    private static Optional<String> evaluate(List<VelocityRule> rules, String amount, Totals day, Totals week) {
        ActivityLookup lookup = (range, currency) -> range.equals(DAY) ? day : week;
        return new VelocityLimitPolicy(rules).evaluate(cad(amount), WEDNESDAY, lookup);
    }

    private static Optional<String> evaluateTaskRules(String amount, Totals day, Totals week) {
        return evaluate(TaskRules.rules(), amount, day, week);
    }

    @Test
    void acceptsFirstLoadWithNoPriorActivity() {
        assertThat(evaluateTaskRules("100.00", Totals.none(CAD), Totals.none(CAD))).isEmpty();
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
        assertThat(evaluateTaskRules("5862.58", Totals.none(CAD), Totals.none(CAD))).contains("DAILY_AMOUNT_LIMIT");
    }

    @Test
    void acceptsWeeklyTotalExactlyAtLimit() {
        assertThat(evaluateTaskRules("5000.00", Totals.none(CAD), totals("15000.00", 0))).isEmpty();
    }

    @Test
    void rejectsWeeklyTotalOneCentOverLimit() {
        assertThat(evaluateTaskRules("1.00", Totals.none(CAD), totals("20000.00", 0))).contains("WEEKLY_AMOUNT_LIMIT");
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
        assertThat(evaluate(List.of(), "999999.00", Totals.none(CAD), Totals.none(CAD))).isEmpty();
    }

    @Test
    void supportsRulesBeyondTheTaskLimits() {
        List<VelocityRule> rules = List.of(
                new VelocityRule("WEEKLY_COUNT_LIMIT", WindowType.WEEK, new Limit.MaxLoads(2)));

        assertThat(evaluate(rules, "1.00", Totals.none(CAD), totals("2.00", 2))).contains("WEEKLY_COUNT_LIMIT");
        assertThat(evaluate(rules, "1.00", Totals.none(CAD), totals("1.00", 1))).isEmpty();
    }

    @Test
    void asksForActivityOncePerDistinctWindowOfTheAttempt() {
        List<TimeRange> asked = new ArrayList<>();
        ActivityLookup lookup = (range, currency) -> {
            asked.add(range);
            return Totals.none(CAD);
        };

        new VelocityLimitPolicy(TaskRules.rules()).evaluate(cad("1.00"), WEDNESDAY, lookup);

        assertThat(asked).containsExactlyInAnyOrder(DAY, WindowType.WEEK.rangeContaining(WEDNESDAY));
    }

    @Test
    void rejectsDuplicateRuleNames() {
        VelocityRule rule = TaskRules.rules().get(0);

        assertThatThrownBy(() -> new VelocityLimitPolicy(List.of(rule, rule)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void asksForTotalsInTheCurrencyOfTheLoad() {
        List<Currency> asked = new ArrayList<>();
        ActivityLookup lookup = (range, currency) -> {
            asked.add(currency);
            return Totals.none(currency);
        };

        new VelocityLimitPolicy(TaskRules.rules()).evaluate(Money.ofMajor(new BigDecimal("1"), USD), WEDNESDAY, lookup);

        assertThat(asked).containsOnly(USD);
    }

    @Test
    void appliesEachAmountRuleOnlyToLoadsInItsOwnCurrency() {
        List<VelocityRule> rules = List.of(
                new VelocityRule("DAILY_AMOUNT_CAD", WindowType.DAY, new Limit.MaxAmount(cad("100"))),
                new VelocityRule("DAILY_AMOUNT_USD", WindowType.DAY, new Limit.MaxAmount(Money.ofMajor(new BigDecimal("50"), USD))));
        VelocityLimitPolicy policy = new VelocityLimitPolicy(rules);
        ActivityLookup nothingYet = (range, currency) -> Totals.none(currency);

        assertThat(policy.evaluate(cad("100.00"), WEDNESDAY, nothingYet)).isEmpty();
        assertThat(policy.evaluate(cad("100.01"), WEDNESDAY, nothingYet)).contains("DAILY_AMOUNT_CAD");
        assertThat(policy.evaluate(Money.ofMajor(new BigDecimal("50"), USD), WEDNESDAY, nothingYet)).isEmpty();
        assertThat(policy.evaluate(Money.ofMajor(new BigDecimal("50.01"), USD), WEDNESDAY, nothingYet))
                .contains("DAILY_AMOUNT_USD");
    }
}
