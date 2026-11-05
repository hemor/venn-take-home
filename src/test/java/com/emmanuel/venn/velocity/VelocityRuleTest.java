package com.emmanuel.venn.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private static VelocityRule amountRule(String max) {
        return new VelocityRule("DAILY_AMOUNT", WindowType.DAY, Metric.AMOUNT, new BigDecimal(max));
    }

    private static VelocityRule countRule(String max) {
        return new VelocityRule("DAILY_COUNT", WindowType.DAY, Metric.COUNT, new BigDecimal(max));
    }

    private static Totals totals(String amount, int count) {
        return new Totals(new BigDecimal(amount), count);
    }

    @Test
    void amountRuleAllowsTotalExactlyAtMax() {
        assertThat(amountRule("5000").isExceededBy(totals("3000.00", 1), new BigDecimal("2000.00"))).isFalse();
    }

    @Test
    void amountRuleIsExceededByOneCentOverMax() {
        assertThat(amountRule("5000").isExceededBy(totals("3000.00", 1), new BigDecimal("2000.01"))).isTrue();
    }

    @Test
    void countRuleAllowsTheLoadThatReachesMax() {
        assertThat(countRule("3").isExceededBy(totals("2.00", 2), new BigDecimal("1.00"))).isFalse();
    }

    @Test
    void countRuleIsExceededByTheLoadAfterMax() {
        assertThat(countRule("3").isExceededBy(totals("3.00", 3), new BigDecimal("1.00"))).isTrue();
    }

    @Test
    void countRuleIgnoresTheAmount() {
        assertThat(countRule("3").isExceededBy(Totals.NONE, new BigDecimal("999999"))).isFalse();
    }

    @Test
    void rejectsNameLongerThanTheStoredRejectionReason() {
        String tooLong = "R".repeat(VelocityRule.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new VelocityRule(tooLong, WindowType.DAY, Metric.AMOUNT, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most " + VelocityRule.MAX_NAME_LENGTH);
    }

    @Test
    void acceptsNameOfExactlyTheMaximumLength() {
        String name = "R".repeat(VelocityRule.MAX_NAME_LENGTH);

        assertThat(new VelocityRule(name, WindowType.DAY, Metric.AMOUNT, BigDecimal.ONE).name()).isEqualTo(name);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new VelocityRule(" ", WindowType.DAY, Metric.AMOUNT, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsNonPositiveMax() {
        assertThatThrownBy(() -> new VelocityRule("R", WindowType.DAY, Metric.AMOUNT, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max");
    }

    @Test
    void rejectsFractionalCountMax() {
        assertThatThrownBy(() -> new VelocityRule("R", WindowType.DAY, Metric.COUNT, new BigDecimal("2.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number");
    }
}
