package com.emmanuel.venn.domain;

import java.math.BigDecimal;

/** A customer's accepted loads within the current day and week. */
public record PriorActivity(BigDecimal dailyTotal, BigDecimal weeklyTotal, int dailyCount) {

    public static final PriorActivity NONE = new PriorActivity(BigDecimal.ZERO, BigDecimal.ZERO, 0);
}
