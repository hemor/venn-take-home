package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("velocity")
record VelocityProperties(List<RuleProperties> rules) {

    record RuleProperties(String name, WindowType window, Metric metric, BigDecimal max, String currency) {

        VelocityRule toRule() {
            try {
                return new VelocityRule(name, window, limit());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("rule " + name + ": " + e.getMessage());
            }
        }

        private Limit limit() {
            if (metric == null || max == null) {
                throw new IllegalArgumentException("needs a metric and a max");
            }
            return switch (metric) {
                case COUNT -> {
                    if (currency != null) {
                        throw new IllegalArgumentException("a COUNT rule has no currency");
                    }
                    yield new Limit.MaxLoads(wholeNumber());
                }
                case AMOUNT -> {
                    if (currency == null) {
                        throw new IllegalArgumentException("an AMOUNT rule needs a currency");
                    }
                    yield new Limit.MaxAmount(Money.ofMajor(max, currencyOf()));
                }
            };
        }

        private int wholeNumber() {
            try {
                return max.intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("max must be a whole number for a COUNT rule");
            }
        }

        private Currency currencyOf() {
            try {
                return Currency.getInstance(currency);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown currency " + currency);
            }
        }
    }

    List<VelocityRule> toRules() {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException("velocity.rules must define at least one rule");
        }
        return rules.stream().map(RuleProperties::toRule).toList();
    }
}
