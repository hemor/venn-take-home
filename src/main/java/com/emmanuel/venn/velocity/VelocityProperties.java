package com.emmanuel.venn.velocity;

import java.util.Currency;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("velocity")
record VelocityProperties(List<RuleProperties> rules) {

    record RuleProperties(String name, WindowType window, Metric metric, Integer max, Long maxInCents,
            String currency) {

        VelocityRule toRule() {
            try {
                return new VelocityRule(name, window, limit());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("rule " + name + ": " + e.getMessage());
            }
        }

        private Limit limit() {
            if (metric == null) {
                throw new IllegalArgumentException("needs a metric");
            }
            return switch (metric) {
                case COUNT -> {
                    require(maxInCents == null, "a COUNT rule is capped with max, not max-in-cents");
                    require(currency == null, "a COUNT rule cannot have a currency yet");
                    require(max != null, "a COUNT rule needs max");
                    yield new Limit.MaxLoads(max);
                }
                case AMOUNT -> {
                    require(max == null, "an AMOUNT rule is capped with max-in-cents, not max");
                    require(maxInCents != null, "an AMOUNT rule needs max-in-cents");
                    yield new Limit.MaxAmount(maxInCents, currency == null ? null : currencyOf());
                }
            };
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(message);
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
