package com.emmanuel.venn.velocity;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("velocity")
record VelocityProperties(List<RuleProperties> rules) {

    record RuleProperties(String name, WindowType window, Metric metric, BigDecimal max, String currency) {

        private Currency currencyOf() {
            try {
                return Currency.getInstance(currency);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("rule " + name + ": unknown currency " + currency);
            }
        }

        VelocityRule toRule() {
            return new VelocityRule(name, window, metric, max, currency == null ? null : currencyOf());
        }
    }

    List<VelocityRule> toRules() {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException("velocity.rules must define at least one rule");
        }
        return rules.stream().map(RuleProperties::toRule).toList();
    }
}
