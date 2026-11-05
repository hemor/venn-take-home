package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VelocityConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(VelocityConfig.class);

    @Test
    void bindsRulesInConfiguredOrder() {
        runner.withPropertyValues(
                "velocity.rules[0].name=PER_MINUTE_COUNT",
                "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT",
                "velocity.rules[0].max=2",
                "velocity.rules[1].name=WEEKLY_CAP",
                "velocity.rules[1].window=WEEK",
                "velocity.rules[1].metric=AMOUNT",
                "velocity.rules[1].max=250.50",
                "velocity.rules[1].currency=CAD")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules()).containsExactly(
                        new VelocityRule("PER_MINUTE_COUNT", WindowType.DAY, new Limit.MaxLoads(2)),
                        new VelocityRule("WEEKLY_CAP", WindowType.WEEK, new Limit.MaxAmount(cad("250.50")))));
    }

    @Test
    void failsStartupWhenNoRulesAreConfigured() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("at least one rule"));
    }

    @Test
    void failsStartupOnAnInvalidRule() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD",
                "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT",
                "velocity.rules[0].max=-5",
                "velocity.rules[0].currency=CAD")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("max must be positive"));
    }

    @Test
    void failsStartupOnAnUnknownWindow() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD",
                "velocity.rules[0].window=FORTNIGHT",
                "velocity.rules[0].metric=AMOUNT",
                "velocity.rules[0].max=5",
                "velocity.rules[0].currency=CAD")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupOnDuplicateRuleNames() {
        runner.withPropertyValues(
                "velocity.rules[0].name=SAME", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=5", "velocity.rules[0].currency=CAD",
                "velocity.rules[1].name=SAME", "velocity.rules[1].window=WEEK",
                "velocity.rules[1].metric=AMOUNT", "velocity.rules[1].max=9", "velocity.rules[1].currency=CAD")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("unique"));
    }

    @Test
    void failsStartupOnAnUnknownCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=5", "velocity.rules[0].currency=ZZZ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("unknown currency ZZZ"));
    }

    @Test
    void failsStartupWhenAnAmountRuleHasNoCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=5")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("needs a currency"));
    }

    @Test
    void failsStartupOnAFractionalLoadLimit() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT", "velocity.rules[0].max=2.5")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("rule BAD: max must be a whole number"));
    }

    @Test
    void failsStartupWhenALoadLimitHasACurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT", "velocity.rules[0].max=2", "velocity.rules[0].currency=CAD")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("a COUNT rule has no currency"));
    }

    @Test
    void failsStartupOnAnAmountFinerThanTheCurrencyAllows() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=1.005", "velocity.rules[0].currency=CAD")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("CAD"));
    }

    @Test
    void readsAmountsInTheDecimalPlacesOfTheirCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=YEN", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=500000", "velocity.rules[0].currency=JPY")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules().get(0).limit())
                        .isEqualTo(new Limit.MaxAmount(new Money(500000, java.util.Currency.getInstance("JPY")))));
    }
}
