package com.emmanuel.venn.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.DuplicateLoadException;
import com.emmanuel.venn.domain.LimitWindows;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.domain.PriorActivity;
import com.emmanuel.venn.domain.RejectionReason;
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
    private static final LimitWindows WEDNESDAY = LimitWindows.containing(WEDNESDAY_NOON);

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
        attempts.save(load("1", "A", "9999.00", WEDNESDAY_NOON), Decision.rejected(RejectionReason.DAILY_AMOUNT_EXCEEDED));

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
        assertThat(attempts.acceptedActivity("A", WEDNESDAY)).isEqualTo(PriorActivity.NONE);
    }

    @Test
    void sumsAcceptedLoadsWithinDayAndWeek() {
        attempts.save(load("1", "A", "100.50", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "200.25", WEDNESDAY_NOON.plusSeconds(60)), Decision.accepted());

        PriorActivity activity = attempts.acceptedActivity("A", WEDNESDAY);

        assertThat(activity.dailyTotal()).isEqualByComparingTo("300.75");
        assertThat(activity.weeklyTotal()).isEqualByComparingTo("300.75");
        assertThat(activity.dailyCount()).isEqualTo(2);
    }

    @Test
    void ignoresRejectedLoads() {
        attempts.save(load("1", "A", "100.00", WEDNESDAY_NOON), Decision.accepted());
        attempts.save(load("2", "A", "9000.00", WEDNESDAY_NOON), Decision.rejected(RejectionReason.DAILY_AMOUNT_EXCEEDED));

        PriorActivity activity = attempts.acceptedActivity("A", WEDNESDAY);

        assertThat(activity.dailyTotal()).isEqualByComparingTo("100.00");
        assertThat(activity.dailyCount()).isEqualTo(1);
    }

    @Test
    void ignoresOtherCustomers() {
        attempts.save(load("1", "B", "100.00", WEDNESDAY_NOON), Decision.accepted());

        assertThat(attempts.acceptedActivity("A", WEDNESDAY)).isEqualTo(PriorActivity.NONE);
    }

    @Test
    void earlierDayInSameWeekCountsTowardWeekOnly() {
        attempts.save(load("1", "A", "400.00", Instant.parse("2018-01-02T10:00:00Z")), Decision.accepted());

        PriorActivity activity = attempts.acceptedActivity("A", WEDNESDAY);

        assertThat(activity.dailyTotal()).isEqualByComparingTo("0");
        assertThat(activity.dailyCount()).isZero();
        assertThat(activity.weeklyTotal()).isEqualByComparingTo("400.00");
    }

    @Test
    void previousWeekIsExcluded() {
        attempts.save(load("1", "A", "400.00", Instant.parse("2018-01-01T00:00:00Z").minusSeconds(1)), Decision.accepted());

        assertThat(attempts.acceptedActivity("A", WEDNESDAY)).isEqualTo(PriorActivity.NONE);
    }

    @Test
    void windowStartIsInclusiveAndEndIsExclusive() {
        attempts.save(load("1", "A", "10.00", WEDNESDAY.dayStart()), Decision.accepted());
        attempts.save(load("2", "A", "20.00", WEDNESDAY.dayEnd()), Decision.accepted());

        PriorActivity activity = attempts.acceptedActivity("A", WEDNESDAY);

        assertThat(activity.dailyTotal()).isEqualByComparingTo("10.00");
        assertThat(activity.dailyCount()).isEqualTo(1);
        assertThat(activity.weeklyTotal()).isEqualByComparingTo("30.00");
    }
}
