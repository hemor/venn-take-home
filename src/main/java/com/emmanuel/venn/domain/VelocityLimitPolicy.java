package com.emmanuel.venn.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VelocityLimitPolicy {

    private final List<VelocityRule> rules;

    public VelocityLimitPolicy(List<VelocityRule> rules) {
        long distinctNames = rules.stream().map(VelocityRule::name).distinct().count();
        if (distinctNames != rules.size()) {
            throw new IllegalArgumentException("rule names must be unique");
        }
        this.rules = List.copyOf(rules);
    }

    public List<VelocityRule> rules() {
        return rules;
    }

    /** @return the name of the first configured rule the load would break, or empty if it is allowed */
    public Optional<String> evaluate(BigDecimal amount, Instant time, ActivityLookup activity) {
        Map<TimeRange, Totals> priorByRange = new HashMap<>();
        for (VelocityRule rule : rules) {
            TimeRange range = rule.window().rangeContaining(time);
            Totals prior = priorByRange.computeIfAbsent(range, activity::acceptedWithin);
            if (rule.isExceededBy(prior, amount)) {
                return Optional.of(rule.name());
            }
        }
        return Optional.empty();
    }
}
