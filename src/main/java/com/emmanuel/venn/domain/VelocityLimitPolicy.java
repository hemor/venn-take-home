package com.emmanuel.venn.domain;

import java.math.BigDecimal;
import java.util.Optional;

public class VelocityLimitPolicy {

    private final VelocityLimits limits;

    public VelocityLimitPolicy(VelocityLimits limits) {
        this.limits = limits;
    }

    public Optional<RejectionReason> evaluate(BigDecimal amount, PriorActivity prior) {
        if (prior.dailyCount() + 1 > limits.dailyCount()) {
            return Optional.of(RejectionReason.DAILY_COUNT_EXCEEDED);
        }
        if (prior.dailyTotal().add(amount).compareTo(limits.dailyAmount()) > 0) {
            return Optional.of(RejectionReason.DAILY_AMOUNT_EXCEEDED);
        }
        if (prior.weeklyTotal().add(amount).compareTo(limits.weeklyAmount()) > 0) {
            return Optional.of(RejectionReason.WEEKLY_AMOUNT_EXCEEDED);
        }
        return Optional.empty();
    }
}
