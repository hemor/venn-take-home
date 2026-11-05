package com.emmanuel.venn.velocity;

import java.util.List;

/** The three limits from the task, in the order reported when several are broken. */
public final class TaskRules {

    private TaskRules() {}

    public static List<VelocityRule> rules() {
        return List.of(
                new VelocityRule("DAILY_COUNT_LIMIT", WindowType.DAY, new Limit.MaxLoads(3)),
                new VelocityRule("DAILY_AMOUNT_LIMIT", WindowType.DAY, new Limit.MaxAmount(500_000, null)),
                new VelocityRule("WEEKLY_AMOUNT_LIMIT", WindowType.WEEK, new Limit.MaxAmount(2_000_000, null)));
    }

    public static VelocityLimitPolicy policy() {
        return new VelocityLimitPolicy(rules());
    }
}
