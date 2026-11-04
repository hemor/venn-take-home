package com.emmanuel.venn.domain;

import java.math.BigDecimal;

/** Sum and number of a customer's accepted loads within a time range. */
public record Totals(BigDecimal amount, int count) {

    public static final Totals NONE = new Totals(BigDecimal.ZERO, 0);
}
