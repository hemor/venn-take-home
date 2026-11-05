package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;

/**
 * At most one {@link Limit} per {@code window}. The name is the rejection reason. An amount limit only applies to
 * loads in its currency, if it has one; a load limit counts every load.
 */
public record VelocityRule(String name, WindowType window, Limit limit) {

    public static final int MAX_NAME_LENGTH = 64;

    public VelocityRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rule name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("rule name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (window == null || limit == null) {
            throw new IllegalArgumentException("rule " + name + " needs a window and a limit");
        }
    }

    public boolean isExceededBy(Totals prior, Money amount) {
        return switch (limit) {
            case Limit.MaxLoads loads -> prior.count() + 1L > loads.max();
            case Limit.MaxAmount cap -> cap.appliesTo(amount.currency())
                    && prior.amount().plus(amount).isGreaterThan(cap.in(amount.currency()));
        };
    }
}
