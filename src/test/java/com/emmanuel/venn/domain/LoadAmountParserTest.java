package com.emmanuel.venn.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoadAmountParserTest {

    @Test
    void parsesDollarAmountWithCents() {
        assertThat(LoadAmountParser.parse("$123.45")).isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    void parsesWholeDollarAmount() {
        assertThat(LoadAmountParser.parse("$5000")).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parsesSingleDecimalDigit() {
        assertThat(LoadAmountParser.parse("$0.5")).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "123.45", "$", "$abc", "$-5.00", "$0", "$0.00", "$1.234", "$1,000.00", "$ 5", "€5"})
    void rejectsMalformedOrNonPositiveAmounts(String raw) {
        assertThatThrownBy(() -> LoadAmountParser.parse(raw))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("load_amount");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> LoadAmountParser.parse(null)).isInstanceOf(InvalidLoadException.class);
    }
}
