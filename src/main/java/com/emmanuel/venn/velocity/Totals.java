package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;
import java.util.Currency;

/** Sum, in one currency, and number, across all currencies, of a customer's accepted loads within a time range. */
public record Totals(Money amount, int count) {

    public static Totals none(Currency currency) {
        return new Totals(Money.zero(currency), 0);
    }
}
