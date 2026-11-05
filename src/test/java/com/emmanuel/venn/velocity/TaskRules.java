package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.cad;

import java.util.List;

/** The three limits from the task, in the order reported when several are broken. */
public final class TaskRules {

    private TaskRules() {}

    public static List<VelocityRule> rules() {
        return List.of(
                new VelocityRule("DAILY_COUNT_LIMIT", WindowType.DAY, new Limit.MaxLoads(3)),
                new VelocityRule("DAILY_AMOUNT_LIMIT", WindowType.DAY, new Limit.MaxAmount(cad("5000"))),
                new VelocityRule("WEEKLY_AMOUNT_LIMIT", WindowType.WEEK, new Limit.MaxAmount(cad("20000"))));
    }

    public static VelocityLimitPolicy policy() {
        return new VelocityLimitPolicy(rules());
    }
}
