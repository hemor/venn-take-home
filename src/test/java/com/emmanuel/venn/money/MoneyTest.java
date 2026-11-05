package com.emmanuel.venn.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    private static final Currency CAD = Currency.getInstance("CAD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency KWD = Currency.getInstance("KWD");

    @ParameterizedTest
    @CsvSource({"CAD,123.45,12345", "CAD,5000,500000", "CAD,0.5,50", "CAD,5000.00,500000", "CAD,0.010,1",
            "JPY,500,500", "KWD,1.234,1234", "KWD,2,2000"})
    void convertsMajorUnitsUsingTheDecimalPlacesOfTheCurrency(String currency, String major, long minor) {
        assertThat(Money.ofMajor(new BigDecimal(major), Currency.getInstance(currency)).minorUnits())
                .isEqualTo(minor);
    }

    @ParameterizedTest
    @CsvSource({"CAD,0.001", "CAD,1.234", "JPY,0.5", "KWD,1.2345"})
    void rejectsAmountsFinerThanTheSmallestUnitOfTheCurrency(String currency, String major) {
        assertThatThrownBy(() -> Money.ofMajor(new BigDecimal(major), Currency.getInstance(currency)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(currency);
    }

    @Test
    void rejectsAmountsTooLargeToStore() {
        assertThatThrownBy(() -> Money.ofMajor(new BigDecimal("1" + "0".repeat(20)), CAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCurrenciesWithoutADecimalPlaceDefinition() {
        assertThatThrownBy(() -> Money.ofMajor(BigDecimal.ONE, Currency.getInstance("XXX")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAmountsOfTheSameCurrency() {
        assertThat(new Money(100, CAD).plus(new Money(250, CAD))).isEqualTo(new Money(350, CAD));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        assertThatThrownBy(() -> new Money(100, CAD).plus(new Money(100, JPY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAD")
                .hasMessageContaining("JPY");
    }

    @Test
    void failsLoudlyOnOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> new Money(Long.MAX_VALUE, CAD).plus(new Money(1, CAD)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void comparesAmountsOfTheSameCurrency() {
        assertThat(new Money(500001, CAD).isGreaterThan(new Money(500000, CAD))).isTrue();
        assertThat(new Money(500000, CAD).isGreaterThan(new Money(500000, CAD))).isFalse();
    }

    @Test
    void refusesToCompareDifferentCurrencies() {
        assertThatThrownBy(() -> new Money(1, CAD).isGreaterThan(new Money(1, JPY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroHasNoMinorUnitsInAnyCurrency() {
        assertThat(Money.zero(JPY)).isEqualTo(new Money(0, JPY));
    }
}
