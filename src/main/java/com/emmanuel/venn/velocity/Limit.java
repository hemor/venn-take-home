package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.util.Currency;

/** What a rule caps: a number of loads, or an amount, optionally in one currency. */
public sealed interface Limit {

    record MaxLoads(int max) implements Limit {

        public MaxLoads {
            if (max <= 0) {
                throw new IllegalArgumentException("max must be positive");
            }
        }
    }

    /**
     * With a currency, applies only to loads in it. Without one, applies to every load and is read in the
     * currency of the load being checked, without conversion.
     */
    record MaxAmount(BigDecimal max, Currency currency) implements Limit {

        public MaxAmount {
            if (max == null || max.signum() <= 0) {
                throw new IllegalArgumentException("max must be positive");
            }
            if (currency != null) {
                Money.ofMajor(max, currency);
            }
        }

        public boolean appliesTo(Currency loadCurrency) {
            return currency == null || currency.equals(loadCurrency);
        }

        public Money in(Currency loadCurrency) {
            return Money.ofMajor(max, loadCurrency);
        }
    }
}
