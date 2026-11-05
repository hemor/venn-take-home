package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.CAD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
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
                "velocity.rules[1].max-in-cents=25050",
                "velocity.rules[1].currency=CAD")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules()).containsExactly(
                        new VelocityRule("PER_MINUTE_COUNT", WindowType.DAY, new Limit.MaxLoads(2)),
                        new VelocityRule("WEEKLY_CAP", WindowType.WEEK, new Limit.MaxAmount(25050, CAD))));
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
                "velocity.rules[0].max-in-cents=-500")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("rule BAD: max-in-cents must be positive"));
    }

    @Test
    void failsStartupOnAnUnknownWindow() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD",
                "velocity.rules[0].window=FORTNIGHT",
                "velocity.rules[0].metric=AMOUNT",
                "velocity.rules[0].max-in-cents=500")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupOnDuplicateRuleNames() {
        runner.withPropertyValues(
                "velocity.rules[0].name=SAME", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max-in-cents=500",
                "velocity.rules[1].name=SAME", "velocity.rules[1].window=WEEK",
                "velocity.rules[1].metric=AMOUNT", "velocity.rules[1].max-in-cents=900")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("unique"));
    }

    @Test
    void failsStartupOnAnUnknownCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max-in-cents=500",
                "velocity.rules[0].currency=ZZZ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("unknown currency ZZZ"));
    }

    @Test
    void readsAnAmountRuleWithoutACurrencyAsApplyingToEveryCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=ANY", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max-in-cents=500")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules().get(0).limit())
                        .isEqualTo(new Limit.MaxAmount(500, null)));
    }

    @Test
    void readsAnAmountInTheMinorUnitsOfItsCurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=YEN", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max-in-cents=500000",
                "velocity.rules[0].currency=JPY")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules().get(0).limit())
                        .isEqualTo(new Limit.MaxAmount(500000, Currency.getInstance("JPY"))));
    }

    @Test
    void failsStartupOnAFractionalLoadLimit() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT", "velocity.rules[0].max=2.5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupWhenALoadLimitHasACurrency() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT", "velocity.rules[0].max=2", "velocity.rules[0].currency=CAD")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("a COUNT rule cannot have a currency yet"));
    }

    @Test
    void failsStartupWhenAnAmountRuleUsesMaxInsteadOfMaxInCents() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=5000")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("an AMOUNT rule is capped with max-in-cents"));
    }

    @Test
    void failsStartupWhenAnAmountRuleHasNoLimit() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY", "velocity.rules[0].metric=AMOUNT")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("an AMOUNT rule needs max-in-cents"));
    }

    @Test
    void failsStartupWhenALoadRuleUsesMaxInCents() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=COUNT", "velocity.rules[0].max-in-cents=500")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("a COUNT rule is capped with max, not max-in-cents"));
    }
}
