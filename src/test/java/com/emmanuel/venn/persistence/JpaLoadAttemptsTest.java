package com.emmanuel.venn.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.DuplicateLoadException;
import com.emmanuel.venn.domain.TimeRange;
import com.emmanuel.venn.domain.Totals;
import com.emmanuel.venn.domain.WindowType;
import com.emmanuel.venn.domain.LoadAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaLoadAttempts.class)
class JpaLoadAttemptsTest {

    private static final Instant WEDNESDAY_NOON = Instant.parse("2018-01-03T12:00:00Z");
    private static final TimeRange DAY = WindowType.DAY.rangeContaining(WEDNESDAY_NOON);
    private static final TimeRange WEEK = WindowType.WEEK.rangeContaining(WEDNESDAY_NOON);

    @Autowired
    private JpaLoadAttempts attempts;

    private static LoadAttempt load(String loadId, String customerId, String amount, Instant time) {
        return new LoadAttempt(loadId, customerId, new BigDecimal(amount), time);
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
        attempts.save(load("1", "A", "9999.00", WEDNESDAY_NOON), Decision.rejected("DAILY_AMOUNT_EXCEEDED"));

        assertThat(attempts.exists("A", "1")).isTrue();
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
        assertThat(attempts.acceptedTotals("A", DAY)).isEqualTo(Totals.NONE);
    }

    @Test
    void sumsAndCountsAcceptedLoadsWithinRange() {
        attempts.save(load("1", "A", "100.50", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "200.25", WEDNESDAY_NOON.plusSeconds(60)), Decision.accepted());

        Totals day = attempts.acceptedTotals("A", DAY);

        assertThat(day.amount()).isEqualByComparingTo("300.75");
        assertThat(day.count()).isEqualTo(2);
        assertThat(attempts.acceptedTotals("A", WEEK).amount()).isEqualByComparingTo("300.75");
    }

    @Test
    void ignoresRejectedLoads() {
        attempts.save(load("1", "A", "100.00", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "9000.00", WEDNESDAY_NOON), Decision.rejected("DAILY_AMOUNT_EXCEEDED"));

        Totals day = attempts.acceptedTotals("A", DAY);

        assertThat(day.amount()).isEqualByComparingTo("100.00");
        assertThat(day.count()).isEqualTo(1);
    }

    @Test
    void ignoresOtherCustomers() {
        attempts.save(load("1", "B", "100.00", WEDNESDAY_NOON), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", DAY)).isEqualTo(Totals.NONE);
    }

    @Test
    void earlierDayInSameWeekCountsTowardWeekOnly() {
        attempts.save(load("1", "A", "400.00", Instant.parse("2018-01-02T10:00:00Z")), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", DAY)).isEqualTo(Totals.NONE);
        assertThat(attempts.acceptedTotals("A", WEEK).amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void previousWeekIsExcluded() {
        attempts.save(load("1", "A", "400.00", WEEK.start().minusSeconds(1)), Decision.accepted());

        assertThat(attempts.acceptedTotals("A", WEEK)).isEqualTo(Totals.NONE);
    }

    @Test
    void rangeStartIsInclusiveAndEndIsExclusive() {
        attempts.save(load("1", "A", "10.00", DAY.start()), Decision.accepted());
        attempts.save(load("2", "A", "20.00", DAY.end()), Decision.accepted());

        Totals day = attempts.acceptedTotals("A", DAY);

        assertThat(day.amount()).isEqualByComparingTo("10.00");
        assertThat(day.count()).isEqualTo(1);
        assertThat(attempts.acceptedTotals("A", WEEK).amount()).isEqualByComparingTo("30.00");
    }
}
