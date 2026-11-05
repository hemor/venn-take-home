package com.emmanuel.venn.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class LimitTest {

    @Test
    void aLoadLimitMustBePositive() {
        assertThatThrownBy(() -> new Limit.MaxLoads(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max must be positive");
    }

    @Test
    void anAmountLimitMustBePositive() {
        assertThatThrownBy(() -> new Limit.MaxAmount(BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max must be positive");
        assertThatThrownBy(() -> new Limit.MaxAmount(new BigDecimal("-5"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAmountLimitMustFitTheDecimalPlacesOfItsCurrency() {
        assertThatThrownBy(() -> new Limit.MaxAmount(new BigDecimal("1.005"), Currency.getInstance("CAD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAD");
    }

    @Test
    void anAmountLimitWithACurrencyAppliesOnlyToThatCurrency() {
        Limit.MaxAmount cad = new Limit.MaxAmount(BigDecimal.TEN, Currency.getInstance("CAD"));

        assertThat(cad.appliesTo(Currency.getInstance("CAD"))).isTrue();
        assertThat(cad.appliesTo(Currency.getInstance("USD"))).isFalse();
    }

    @Test
    void anAmountLimitWithoutACurrencyAppliesToEveryCurrencyInItsOwnUnits() {
        Limit.MaxAmount any = new Limit.MaxAmount(new BigDecimal("5000"), null);

        assertThat(any.appliesTo(Currency.getInstance("CAD"))).isTrue();
        assertThat(any.appliesTo(Currency.getInstance("JPY"))).isTrue();
        assertThat(any.in(Currency.getInstance("CAD")).minorUnits()).isEqualTo(500000);
        assertThat(any.in(Currency.getInstance("JPY")).minorUnits()).isEqualTo(5000);
    }
}
