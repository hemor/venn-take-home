package com.emmanuel.venn.loads;

import static com.emmanuel.venn.money.TestMoney.cad;
import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.loads.attempts.Decision;
import com.emmanuel.venn.loads.attempts.LoadAttempt;
import com.emmanuel.venn.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Supporting more currencies is configuration: no production code in this test's setup knows about them. */
@SpringBootTest(properties = {
        "velocity.rules[0].name=DAILY_COUNT_LIMIT",
        "velocity.rules[0].window=DAY",
        "velocity.rules[0].metric=COUNT",
        "velocity.rules[0].max=3",
        "velocity.rules[1].name=DAILY_AMOUNT_LIMIT_CAD",
        "velocity.rules[1].window=DAY",
        "velocity.rules[1].metric=AMOUNT",
        "velocity.rules[1].max=5000",
        "velocity.rules[1].currency=CAD",
        "velocity.rules[2].name=DAILY_AMOUNT_LIMIT_USD",
        "velocity.rules[2].window=DAY",
        "velocity.rules[2].metric=AMOUNT",
        "velocity.rules[2].max=3000",
        "velocity.rules[2].currency=USD",
        "velocity.rules[3].name=DAILY_AMOUNT_LIMIT_JPY",
        "velocity.rules[3].window=DAY",
        "velocity.rules[3].metric=AMOUNT",
        "velocity.rules[3].max=500000",
        "velocity.rules[3].currency=JPY"})
class AdditionalCurrencyTest {

    private static final Instant TIME = Instant.parse("2018-01-01T00:00:00Z");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Autowired
    private LoadService service;

    @Autowired
    private JdbcTemplate jdbc;

    private Optional<Decision> load(String customer, String id, Money amount) {
        return service.process(new LoadAttempt(id, customer, amount, TIME));
    }

    private static Money usd(String major) {
        return Money.ofMajor(new BigDecimal(major), USD);
    }

    private static Money jpy(String major) {
        return Money.ofMajor(new BigDecimal(major), JPY);
    }

    @Test
    void eachCurrencyHasItsOwnAmountLimit() {
        assertThat(load("multi-1", "1", cad("5000.00")).orElseThrow().isAccepted()).isTrue();
        assertThat(load("multi-1", "2", usd("3000.00")).orElseThrow().isAccepted()).isTrue();
        assertThat(load("multi-2", "1", usd("3000.01")).orElseThrow().rejectionReason())
                .isEqualTo("DAILY_AMOUNT_LIMIT_USD");
        assertThat(load("multi-3", "1", cad("5000.01")).orElseThrow().rejectionReason())
                .isEqualTo("DAILY_AMOUNT_LIMIT_CAD");
    }

    @Test
    void aCurrencyWithoutDecimalPlacesIsLimitedInWholeUnits() {
        assertThat(load("multi-4", "1", jpy("500000")).orElseThrow().isAccepted()).isTrue();
        assertThat(load("multi-4", "2", jpy("1")).orElseThrow().rejectionReason()).isEqualTo("DAILY_AMOUNT_LIMIT_JPY");
    }

    @Test
    void theLoadCountLimitCountsLoadsInEveryCurrency() {
        load("multi-5", "1", cad("1.00"));
        load("multi-5", "2", usd("1.00"));
        load("multi-5", "3", jpy("1"));

        assertThat(load("multi-5", "4", cad("1.00")).orElseThrow().rejectionReason())
                .isEqualTo("DAILY_COUNT_LIMIT");
    }

    @Test
    void storesTheMinorUnitsAndCurrencyOfEachLoad() {
        load("multi-6", "1", cad("12.34"));
        load("multi-6", "2", jpy("500"));

        assertThat(jdbc.queryForObject(
                "select amount_minor from load_attempt where customer_id = 'multi-6' and currency = 'CAD'", Long.class))
                .isEqualTo(1234);
        assertThat(jdbc.queryForObject(
                "select amount_minor from load_attempt where customer_id = 'multi-6' and currency = 'JPY'", Long.class))
                .isEqualTo(500);
    }
}
