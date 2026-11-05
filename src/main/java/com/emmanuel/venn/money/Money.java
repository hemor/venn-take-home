package com.emmanuel.venn.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** An exact amount in the smallest unit of its currency, such as cents. */
public record Money(long minorUnits, Currency currency) {

    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /** @throws IllegalArgumentException if the amount has finer digits than the currency or does not fit in a long */
    public static Money ofMajor(BigDecimal major, Currency currency) {
        int digits = decimalPlaces(currency);
        try {
            return new Money(major.setScale(digits, RoundingMode.UNNECESSARY).unscaledValue().longValueExact(),
                    currency);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    major.toPlainString() + " " + currency + " is not a storable amount with " + digits
                            + " decimal places", e);
        }
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits, decimalPlaces(currency));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return minorUnits > other.minorUnits;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot combine " + currency + " with " + other.currency);
        }
    }

    private static int decimalPlaces(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException(currency + " has no decimal places and cannot hold an amount");
        }
        return digits;
    }
}
