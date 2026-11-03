package com.emmanuel.venn.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VelocityLimitPolicyTest {

    private final VelocityLimitPolicy policy = new VelocityLimitPolicy(
            new VelocityLimits(new BigDecimal("5000"), new BigDecimal("20000"), 3));

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static PriorActivity prior(String dailyTotal, String weeklyTotal, int dailyCount) {
        return new PriorActivity(amount(dailyTotal), amount(weeklyTotal), dailyCount);
    }

    @Test
    void acceptsFirstLoadWithNoPriorActivity() {
        assertThat(policy.evaluate(amount("100.00"), PriorActivity.NONE)).isEmpty();
    }

    @Test
    void acceptsDailyTotalExactlyAtLimit() {
        assertThat(policy.evaluate(amount("2000.00"), prior("3000.00", "3000.00", 1))).isEmpty();
    }

    @Test
    void rejectsDailyTotalOneCentOverLimit() {
        assertThat(policy.evaluate(amount("2000.01"), prior("3000.00", "3000.00", 1)))
                .contains(RejectionReason.DAILY_AMOUNT_EXCEEDED);
    }

    @Test
    void rejectsSingleLoadOverDailyLimit() {
        assertThat(policy.evaluate(amount("5862.58"), PriorActivity.NONE))
                .contains(RejectionReason.DAILY_AMOUNT_EXCEEDED);
    }

    @Test
    void acceptsWeeklyTotalExactlyAtLimit() {
        assertThat(policy.evaluate(amount("5000.00"), prior("0", "15000.00", 0))).isEmpty();
    }

    @Test
    void rejectsWeeklyTotalOneCentOverLimit() {
        assertThat(policy.evaluate(amount("1.00"), prior("0", "20000.00", 0)))
                .contains(RejectionReason.WEEKLY_AMOUNT_EXCEEDED);
    }

    @Test
    void acceptsThirdLoadOfTheDay() {
        assertThat(policy.evaluate(amount("1.00"), prior("2.00", "2.00", 2))).isEmpty();
    }

    @Test
    void rejectsFourthLoadOfTheDayRegardlessOfAmount() {
        assertThat(policy.evaluate(amount("1.00"), prior("3.00", "3.00", 3)))
                .contains(RejectionReason.DAILY_COUNT_EXCEEDED);
    }

    @Test
    void reportsDailyCountFirstWhenSeveralLimitsAreBroken() {
        Optional<RejectionReason> reason = policy.evaluate(amount("6000.00"), prior("0", "20000.00", 3));

        assertThat(reason).contains(RejectionReason.DAILY_COUNT_EXCEEDED);
    }
}
