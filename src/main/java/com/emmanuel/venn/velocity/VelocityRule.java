package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * At most {@code max} per {@code window}: a number of loads, or an amount in {@code currency}. The name is the
 * rejection reason. An amount rule only applies to loads in its currency; a count rule counts every load.
 */
public record VelocityRule(String name, WindowType window, Metric metric, BigDecimal max, Currency currency) {

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
        if (metric == Metric.COUNT) {
            if (max.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("rule " + name + ": max must be a whole number for a COUNT rule");
            }
            if (currency != null) {
                throw new IllegalArgumentException("rule " + name + ": a COUNT rule has no currency");
            }
        } else {
            if (currency == null) {
                throw new IllegalArgumentException("rule " + name + ": an AMOUNT rule needs a currency");
            }
            Money.ofMajor(max, currency);
        }
    }

    public boolean isExceededBy(Totals prior, Money amount) {
        return switch (metric) {
            case AMOUNT -> amount.currency().equals(currency)
                    && prior.amount().plus(amount).isGreaterThan(Money.ofMajor(max, currency));
            case COUNT -> prior.count() + 1L > max.longValueExact();
        };
    }
}
