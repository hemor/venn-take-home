package com.emmanuel.venn.velocity;

import com.emmanuel.venn.money.Money;

/** What a rule caps: a number of loads, or an amount in one currency. */
public sealed interface Limit {

    record MaxLoads(int max) implements Limit {

        public MaxLoads {
            if (max <= 0) {
                throw new IllegalArgumentException("max must be positive");
            }
        }
    }

    record MaxAmount(Money max) implements Limit {

        public MaxAmount {
            if (max.minorUnits() <= 0) {
                throw new IllegalArgumentException("max must be positive");
            }
        }
    }
}
