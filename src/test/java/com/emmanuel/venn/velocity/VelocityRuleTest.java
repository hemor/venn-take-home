package com.emmanuel.venn.velocity;

import static com.emmanuel.venn.money.TestMoney.CAD;
import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static VelocityRule amountRule(long maxInCents) {
        return new VelocityRule("DAILY_AMOUNT", WindowType.DAY, new Limit.MaxAmount(maxInCents, CAD));
    }

    private static VelocityRule countRule(int max) {
        return new VelocityRule("DAILY_COUNT", WindowType.DAY, new Limit.MaxLoads(max));
    }

    private static Totals totals(String amount, int count) {
        return new Totals(cad(amount), count);
    }

    @Test
    void amountRuleAllowsTotalExactlyAtMax() {
        assertThat(amountRule(500_000).isExceededBy(totals("3000.00", 1), cad("2000.00"))).isFalse();
    }

    @Test
    void amountRuleIsExceededByOneCentOverMax() {
        assertThat(amountRule(500_000).isExceededBy(totals("3000.00", 1), cad("2000.01"))).isTrue();
    }

    @Test
    void countRuleAllowsTheLoadThatReachesMax() {
        assertThat(countRule(3).isExceededBy(totals("2.00", 2), cad("1.00"))).isFalse();
    }

    @Test
    void countRuleIsExceededByTheLoadAfterMax() {
        assertThat(countRule(3).isExceededBy(totals("3.00", 3), cad("1.00"))).isTrue();
    }

    @Test
    void countRuleIgnoresTheAmount() {
        assertThat(countRule(3).isExceededBy(Totals.none(cad("0").currency()), cad("999999"))).isFalse();
    }

    @Test
    void anAmountRuleDoesNotApplyToLoadsInAnotherCurrency() {
        Money usdLoad = Money.ofMajor(new BigDecimal("999999"), USD);

        assertThat(amountRule(500_000).isExceededBy(Totals.none(USD), usdLoad)).isFalse();
    }

    @Test
    void anAmountRuleInAnotherCurrencyAppliesToLoadsInThatCurrency() {
        VelocityRule usdRule = new VelocityRule("DAILY_AMOUNT_USD", WindowType.DAY,
                new Limit.MaxAmount(10_000, USD));

        assertThat(usdRule.isExceededBy(Totals.none(USD), Money.ofMajor(new BigDecimal("100.01"), USD))).isTrue();
        assertThat(usdRule.isExceededBy(Totals.none(USD), Money.ofMajor(new BigDecimal("100.00"), USD))).isFalse();
    }

    @Test
    void anAmountRuleWithoutACurrencyAppliesToLoadsInAnyCurrency() {
        VelocityRule anyCurrency = new VelocityRule("DAILY_AMOUNT", WindowType.DAY,
                new Limit.MaxAmount(500_000, null));

        assertThat(anyCurrency.isExceededBy(Totals.none(CAD), cad("5000.01"))).isTrue();
        assertThat(anyCurrency.isExceededBy(Totals.none(USD), Money.ofMajor(new BigDecimal("5000.01"), USD))).isTrue();
        assertThat(anyCurrency.isExceededBy(Totals.none(USD), Money.ofMajor(new BigDecimal("5000"), USD))).isFalse();
    }

    @Test
    void rejectsNameLongerThanTheStoredRejectionReason() {
        String tooLong = "R".repeat(VelocityRule.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new VelocityRule(tooLong, WindowType.DAY, new Limit.MaxLoads(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most " + VelocityRule.MAX_NAME_LENGTH);
    }

    @Test
    void acceptsNameOfExactlyTheMaximumLength() {
        String name = "R".repeat(VelocityRule.MAX_NAME_LENGTH);

        assertThat(new VelocityRule(name, WindowType.DAY, new Limit.MaxLoads(1)).name()).isEqualTo(name);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new VelocityRule(" ", WindowType.DAY, new Limit.MaxLoads(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void needsAWindowAndALimit() {
        assertThatThrownBy(() -> new VelocityRule("R", null, new Limit.MaxLoads(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
        assertThatThrownBy(() -> new VelocityRule("R", WindowType.DAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
