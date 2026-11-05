package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThatThrownBy(() -> new Limit.MaxAmount(cad("0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max must be positive");
        assertThatThrownBy(() -> new Limit.MaxAmount(cad("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
