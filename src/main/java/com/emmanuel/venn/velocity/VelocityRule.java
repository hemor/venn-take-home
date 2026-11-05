package com.emmanuel.venn.velocity;

import java.math.BigDecimal;

/** At most {@code max} (an amount or a number of loads) per {@code window}. The name is the rejection reason. */
public record VelocityRule(String name, WindowType window, Metric metric, BigDecimal max) {

    public static final int MAX_NAME_LENGTH = 64;

    public VelocityRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rule name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("rule name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (window == null || metric == null) {
            throw new IllegalArgumentException("rule " + name + " needs a window and a metric");
        }
        if (max == null || max.signum() <= 0) {
            throw new IllegalArgumentException("rule " + name + ": max must be positive");
        }
        if (metric == Metric.COUNT && max.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("rule " + name + ": max must be a whole number for a COUNT rule");
        }
    }

    public boolean isExceededBy(Totals prior, BigDecimal amount) {
        BigDecimal total = switch (metric) {
            case AMOUNT -> prior.amount().add(amount);
            case COUNT -> BigDecimal.valueOf(prior.count() + 1L);
        };
        return total.compareTo(max) > 0;
    }
}
