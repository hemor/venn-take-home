package com.emmanuel.venn.velocity;

import java.util.Currency;

@FunctionalInterface
public interface ActivityLookup {

    Totals acceptedWithin(TimeRange range, Currency amountCurrency);
}
