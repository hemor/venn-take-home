package com.emmanuel.venn.velocity;

import java.math.BigDecimal;
import java.util.List;

/** The three limits from the task, in the order reported when several are broken. */
public final class TaskRules {

    private TaskRules() {}

    public static List<VelocityRule> rules() {
        return List.of(
                new VelocityRule("DAILY_COUNT_LIMIT", WindowType.DAY, Metric.COUNT, new BigDecimal("3")),
                new VelocityRule("DAILY_AMOUNT_LIMIT", WindowType.DAY, Metric.AMOUNT, new BigDecimal("5000")),
                new VelocityRule("WEEKLY_AMOUNT_LIMIT", WindowType.WEEK, Metric.AMOUNT, new BigDecimal("20000")));
    }

    public static VelocityLimitPolicy policy() {
        return new VelocityLimitPolicy(rules());
    }
}
