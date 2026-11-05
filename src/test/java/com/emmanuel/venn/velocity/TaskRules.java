package com.emmanuel.venn.velocity;

import java.math.BigDecimal;
import java.util.List;

/** The three limits from the task, in the order reported when several are broken. */
public final class TaskRules {

    private TaskRules() {}

    public static List<VelocityRule> rules() {
        return List.of(
                new VelocityRule("DAILY_COUNT_LIMIT", WindowType.DAY, new Limit.MaxLoads(3)),
                new VelocityRule("DAILY_AMOUNT_LIMIT", WindowType.DAY, new Limit.MaxAmount(new BigDecimal("5000"), null)),
                new VelocityRule("WEEKLY_AMOUNT_LIMIT", WindowType.WEEK, new Limit.MaxAmount(new BigDecimal("20000"), null)));
    }

    public static VelocityLimitPolicy policy() {
        return new VelocityLimitPolicy(rules());
    }
}
