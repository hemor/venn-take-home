package com.emmanuel.venn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.domain.TaskRules;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadServiceTest {

    private static final Instant MONDAY = Instant.parse("2018-01-01T09:00:00Z");

    private final InMemoryLoadAttempts store = new InMemoryLoadAttempts();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final LoadService service = new LoadService(store,
            TaskRules.policy(),
            meters);

    private static LoadAttempt load(String loadId, String customerId, String amount, Instant time) {
        return new LoadAttempt(loadId, customerId, new BigDecimal(amount), time);
    }

    private double count(String outcome) {
        return meters.find("loads.processed").tag("outcome", outcome).counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    @Test
    void acceptsLoadWithinLimitsAndStoresIt() {
        Optional<Decision> decision = service.process(load("1", "A", "100.00", MONDAY));

        assertThat(decision).contains(Decision.accepted());
        assertThat(store.stored).hasSize(1);
    }

    @Test
    void rejectsLoadOverDailyAmountAndStoresReason() {
        Optional<Decision> decision = service.process(load("1", "A", "5000.01", MONDAY));

        assertThat(decision).contains(Decision.rejected("DAILY_AMOUNT_EXCEEDED"));
        assertThat(store.stored.get(0).decision().rejectionReason()).isEqualTo("DAILY_AMOUNT_EXCEEDED");
    }

    @Test
    void secondThreeThousandLoadInADayIsDeclined() {
        service.process(load("1", "A", "3000.00", MONDAY));

        assertThat(service.process(load("2", "A", "3000.00", MONDAY.plusSeconds(60))))
                .contains(Decision.rejected("DAILY_AMOUNT_EXCEEDED"));
    }

    @Test
    void fourthFourHundredDollarLoadInADayIsDeclined() {
        service.process(load("1", "A", "400.00", MONDAY));
        service.process(load("2", "A", "400.00", MONDAY.plusSeconds(1)));
        service.process(load("3", "A", "400.00", MONDAY.plusSeconds(2)));

        assertThat(service.process(load("4", "A", "400.00", MONDAY.plusSeconds(3))))
                .contains(Decision.rejected("DAILY_COUNT_EXCEEDED"));
    }

    @Test
    void rejectedLoadsDoNotCountTowardLimits() {
        service.process(load("1", "A", "6000.00", MONDAY));

        assertThat(service.process(load("2", "A", "5000.00", MONDAY.plusSeconds(1)))).contains(Decision.accepted());
    }

    @Test
    void dailyLimitsResetAtMidnightUtc() {
        service.process(load("1", "A", "5000.00", MONDAY));

        assertThat(service.process(load("2", "A", "5000.00", Instant.parse("2018-01-02T00:00:00Z"))))
                .contains(Decision.accepted());
    }

    @Test
    void weeklyLimitAppliesAcrossDays() {
        for (int day = 0; day < 4; day++) {
            service.process(load("d" + day, "A", "5000.00", MONDAY.plusSeconds(day * 86_400L)));
        }

        assertThat(service.process(load("x", "A", "1.00", MONDAY.plusSeconds(4 * 86_400L))))
                .contains(Decision.rejected("WEEKLY_AMOUNT_EXCEEDED"));
    }

    @Test
    void weeklyLimitResetsOnMondayMidnightUtc() {
        for (int day = 0; day < 4; day++) {
            service.process(load("d" + day, "A", "5000.00", MONDAY.plusSeconds(day * 86_400L)));
        }

        assertThat(service.process(load("x", "A", "5000.00", Instant.parse("2018-01-08T00:00:00Z"))))
                .contains(Decision.accepted());
    }

    @Test
    void customersAreIndependent() {
        service.process(load("1", "A", "5000.00", MONDAY));

        assertThat(service.process(load("1", "B", "5000.00", MONDAY))).contains(Decision.accepted());
    }

    @Test
    void ignoresRepeatedLoadIdForSameCustomer() {
        service.process(load("1", "A", "100.00", MONDAY));

        assertThat(service.process(load("1", "A", "200.00", MONDAY.plusSeconds(1)))).isEmpty();
        assertThat(store.stored).hasSize(1);
    }

    @Test
    void ignoresRepeatOfRejectedLoadInsteadOfReevaluatingIt() {
        service.process(load("1", "A", "6000.00", MONDAY));

        assertThat(service.process(load("1", "A", "100.00", MONDAY.plusSeconds(1)))).isEmpty();
    }

    @Test
    void treatsConcurrentDuplicateAsIgnored() {
        store.failNextSaveAsDuplicate = true;

        assertThat(service.process(load("1", "A", "100.00", MONDAY))).isEmpty();
    }

    @Test
    void countsOutcomes() {
        service.process(load("1", "A", "100.00", MONDAY));
        service.process(load("2", "A", "9000.00", MONDAY.plusSeconds(1)));
        service.process(load("1", "A", "100.00", MONDAY.plusSeconds(2)));

        assertThat(count("accepted")).isEqualTo(1);
        assertThat(count("rejected")).isEqualTo(1);
        assertThat(count("duplicate")).isEqualTo(1);
    }

    @Test
    void tagsRejectionsWithReason() {
        service.process(load("1", "A", "9000.00", MONDAY));

        assertThat(meters.counter("loads.processed", "outcome", "rejected",
                "reason", "DAILY_AMOUNT_EXCEEDED").count()).isEqualTo(1);
    }
}
