package com.emmanuel.venn.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
                "velocity.rules[1].max=250.50")
                .run(context -> assertThat(context.getBean(VelocityLimitPolicy.class).rules()).containsExactly(
                        new VelocityRule("PER_MINUTE_COUNT", WindowType.DAY, Metric.COUNT, new BigDecimal("2")),
                        new VelocityRule("WEEKLY_CAP", WindowType.WEEK, Metric.AMOUNT, new BigDecimal("250.50"))));
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
                "velocity.rules[0].max=-5")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("max must be positive"));
    }

    @Test
    void failsStartupOnAnUnknownWindow() {
        runner.withPropertyValues(
                "velocity.rules[0].name=BAD",
                "velocity.rules[0].window=FORTNIGHT",
                "velocity.rules[0].metric=AMOUNT",
                "velocity.rules[0].max=5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupOnDuplicateRuleNames() {
        runner.withPropertyValues(
                "velocity.rules[0].name=SAME", "velocity.rules[0].window=DAY",
                "velocity.rules[0].metric=AMOUNT", "velocity.rules[0].max=5",
                "velocity.rules[1].name=SAME", "velocity.rules[1].window=WEEK",
                "velocity.rules[1].metric=AMOUNT", "velocity.rules[1].max=9")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("unique"));
    }
}
