package com.emmanuel.venn.loads.attempts;

import static com.emmanuel.venn.money.TestMoney.CAD;
import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.money.Money;
import com.emmanuel.venn.money.TestMoney;
import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;
import com.emmanuel.venn.velocity.VelocityRule;
import com.emmanuel.venn.velocity.WindowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAttemptHistory.class)
class JpaAttemptHistoryTest {

    private static final Instant WEDNESDAY_NOON = Instant.parse("2018-01-03T12:00:00Z");
    private static final TimeRange DAY = WindowType.DAY.rangeContaining(WEDNESDAY_NOON);
    private static final TimeRange WEEK = WindowType.WEEK.rangeContaining(WEDNESDAY_NOON);

    @Autowired
    private JpaAttemptHistory attempts;

    private static LoadAttempt load(String loadId, String customerId, String amount, Instant time) {
        return new LoadAttempt(loadId, customerId, TestMoney.cad(amount), time);
    }

    @Test
    void remembersSavedAttemptPerCustomer() {
        attempts.save(load("1", "A", "10.00", WEDNESDAY_NOON), Decision.accepted());

        assertThat(attempts.exists("A", "1")).isTrue();
        assertThat(attempts.exists("B", "1")).isFalse();
        assertThat(attempts.exists("A", "2")).isFalse();
    }

    @Test
    void remembersRejectedAttemptsToo() {
        attempts.save(load("1", "A", "9999.00", WEDNESDAY_NOON), Decision.rejected("DAILY_AMOUNT_LIMIT"));

        assertThat(attempts.exists("A", "1")).isTrue();
    }

    @Test
    void storesTheLargestValuesTheApplicationAllows() {
        String longestId = "i".repeat(64);
        String longestReason = "R".repeat(VelocityRule.MAX_NAME_LENGTH);
        String largestAmount = "9".repeat(15) + ".99";

        attempts.save(load(longestId, longestId, largestAmount, WEDNESDAY_NOON), Decision.rejected(longestReason));

        assertThat(attempts.exists(longestId, longestId)).isTrue();
    }

    @Test
    void refusesToTakeTheCustomerLockInsideAnotherTransaction() {
        assertThatThrownBy(() -> attempts.withCustomerLock("A", () -> "never runs"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
    }

    @Test
    void rejectsSecondSaveOfSameCustomerAndLoadId() {
        attempts.save(load("1", "A", "10.00", WEDNESDAY_NOON), Decision.accepted());

        assertThatThrownBy(() -> attempts.save(load("1", "A", "20.00", WEDNESDAY_NOON), Decision.accepted()))
                .isInstanceOf(DuplicateLoadException.class);
    }

    @Test
    void doesNotReportOtherIntegrityViolationsAsDuplicates() {
        LoadAttempt withoutLoadId = load(null, "A", "10.00", WEDNESDAY_NOON);

        assertThatThrownBy(() -> attempts.save(withoutLoadId, Decision.accepted()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameLoadIdForDifferentCustomers() {
        attempts.save(load("1", "A", "10.00", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("1", "B", "10.00", WEDNESDAY_NOON), Decision.accepted());

        assertThat(attempts.exists("B", "1")).isTrue();
    }

    @Test
    void reportsNoActivityForUnknownCustomer() {
        assertThat(attempts.acceptedTotals("A", DAY, CAD)).isEqualTo(Totals.none(CAD));
    }

    @Test
    void sumsAndCountsAcceptedLoadsWithinRange() {
        attempts.save(load("1", "A", "100.50", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "200.25", WEDNESDAY_NOON.plusSeconds(60)), Decision.accepted());

        Totals day = attempts.acceptedTotals("A", DAY, CAD);

        assertThat(day.amount()).isEqualTo(cad("300.75"));
        assertThat(day.count()).isEqualTo(2);
        assertThat(attempts.acceptedTotals("A", WEEK, CAD).amount()).isEqualTo(cad("300.75"));
    }

    @Test
    void ignoresRejectedLoads() {
        attempts.save(load("1", "A", "100.00", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "9000.00", WEDNESDAY_NOON), Decision.rejected("DAILY_AMOUNT_LIMIT"));

        Totals day = attempts.acceptedTotals("A", DAY, CAD);

        assertThat(day.amount()).isEqualTo(cad("100.00"));
        assertThat(day.count()).isEqualTo(1);
    }

    @Test
    void ignoresOtherCustomers() {
        attempts.save(load("1", "B", "100.00", WEDNESDAY_NOON), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", DAY, CAD)).isEqualTo(Totals.none(CAD));
    }

    @Test
    void earlierDayInSameWeekCountsTowardWeekOnly() {
        attempts.save(load("1", "A", "400.00", Instant.parse("2018-01-02T10:00:00Z")), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", DAY, CAD)).isEqualTo(Totals.none(CAD));
        assertThat(attempts.acceptedTotals("A", WEEK, CAD).amount()).isEqualTo(cad("400.00"));
    }

    @Test
    void previousWeekIsExcluded() {
        attempts.save(load("1", "A", "400.00", WEEK.start().minusSeconds(1)), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", WEEK, CAD)).isEqualTo(Totals.none(CAD));
    }

    @Test
    void rangeStartIsInclusiveAndEndIsExclusive() {
        attempts.save(load("1", "A", "10.00", DAY.start()), Decision.accepted());
        attempts.save(load("2", "A", "20.00", DAY.end()), Decision.accepted());

        Totals day = attempts.acceptedTotals("A", DAY, CAD);

        assertThat(day.amount()).isEqualTo(cad("10.00"));
        assertThat(day.count()).isEqualTo(1);
        assertThat(attempts.acceptedTotals("A", WEEK, CAD).amount()).isEqualTo(cad("30.00"));
    }

    @Test
    void sumsEachCurrencyInItsOwnUnitsAndCountsLoadsOfAllCurrencies() {
        Currency usd = Currency.getInstance("USD");
        Currency jpy = Currency.getInstance("JPY");
        Currency kwd = Currency.getInstance("KWD");
        attempts.save(load("1", "A", "100.50", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(loadIn("2", "A", "50.25", usd), Decision.accepted());
        attempts.save(loadIn("3", "A", "500", jpy), Decision.accepted());
        attempts.save(loadIn("4", "A", "1.234", kwd), Decision.accepted());
        attempts.save(loadIn("5", "A", "9.99", usd), Decision.rejected("DAILY_AMOUNT_LIMIT_USD"));

        assertThat(attempts.acceptedTotals("A", DAY, CAD)).isEqualTo(new Totals(cad("100.50"), 4));
        assertThat(attempts.acceptedTotals("A", DAY, usd).amount()).isEqualTo(Money.ofMajor(new BigDecimal("50.25"), usd));
        assertThat(attempts.acceptedTotals("A", DAY, jpy).amount()).isEqualTo(new Money(500, jpy));
        assertThat(attempts.acceptedTotals("A", DAY, kwd).amount()).isEqualTo(new Money(1234, kwd));
    }

    @Test
    void reportsZeroInTheRequestedCurrencyWhenOnlyOtherCurrenciesHaveLoads() {
        Currency usd = Currency.getInstance("USD");
        attempts.save(loadIn("1", "A", "10.00", usd), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", DAY, CAD)).isEqualTo(new Totals(cad("0"), 1));
    }

    private static LoadAttempt loadIn(String loadId, String customerId, String major, Currency currency) {
        return new LoadAttempt(loadId, customerId, Money.ofMajor(new BigDecimal(major), currency), WEDNESDAY_NOON);
    }
}
