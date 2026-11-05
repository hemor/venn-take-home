package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;
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
     * minor units of the load's own currency, without conversion.
     */
    record MaxAmount(long maxMinorUnits, Currency currency) implements Limit {

        public MaxAmount {
            if (maxMinorUnits <= 0) {
                throw new IllegalArgumentException("max-in-cents must be positive");
            }
        }

        public boolean appliesTo(Currency loadCurrency) {
            return currency == null || currency.equals(loadCurrency);
        }

        public Money in(Currency loadCurrency) {
            return new Money(maxMinorUnits, loadCurrency);
        }
    }
}
