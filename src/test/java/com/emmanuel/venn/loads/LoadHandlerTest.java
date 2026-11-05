package com.emmanuel.venn.loads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.loads.attempts.InMemoryAttemptHistory;
import com.emmanuel.venn.velocity.TaskRules;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoadHandlerTest {

    private final InMemoryAttemptHistory store = new InMemoryAttemptHistory();
    private final LoadHandler handler = new LoadHandler(
            new LoadService(store,
                    TaskRules.policy(),
                    new SimpleMeterRegistry()),
            Validation.buildDefaultValidatorFactory().getValidator());

    private static LoadRequest request(String id, String customerId, String amount, String time) {
        return new LoadRequest(id, customerId, amount, time);
    }

    @Test
    void acceptedLoadEchoesIdsWithAcceptedTrue() {
        Optional<LoadResponse> response = handler.handle(request("1234", "99", "$123.45", "2018-01-01T00:00:00Z"));

        assertThat(response).contains(new LoadResponse("1234", "99", true));
    }

    @Test
    void declinedLoadHasAcceptedFalse() {
        Optional<LoadResponse> response = handler.handle(request("1", "99", "$5000.01", "2018-01-01T00:00:00Z"));

        assertThat(response).contains(new LoadResponse("1", "99", false));
    }

    @Test
    void repeatedLoadHasNoResponse() {
        handler.handle(request("1", "99", "$10.00", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request("1", "99", "$10.00", "2018-01-01T00:00:01Z"))).isEmpty();
    }

    @Test
    void timeOffsetIsConvertedToUtcBeforeApplyingDailyLimits() {
        handler.handle(request("1", "99", "$5000.00", "2018-01-01T23:00:00-05:00"));

        Optional<LoadResponse> sameUtcDay = handler.handle(request("2", "99", "$1.00", "2018-01-02T04:30:00Z"));

        assertThat(sameUtcDay).contains(new LoadResponse("2", "99", false));
    }

    @Test
    void rejectsMissingId() {
        assertThatThrownBy(() -> handler.handle(request(null, "99", "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsBlankCustomerId() {
        assertThatThrownBy(() -> handler.handle(request("1", " ", "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("customer_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "12.50", "$", "$abc", "$-5.00", "$0", "$0.00", "$1.234", "$1,000.00", "$ 5", "€5", "$1e3", "$+5", "$١٢٣", "$5.", "$.5", "5$"})
    void rejectsMalformedOrNonPositiveAmounts(String amount) {
        assertThatThrownBy(() -> handler.handle(request("1", "99", amount, "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("load_amount");
    }

    @Test
    void readsWholeDollarAmountsAsDollars() {
        handler.handle(request("1", "99", "$5000", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request("2", "99", "$0.01", "2018-01-01T00:00:01Z")))
                .contains(new LoadResponse("2", "99", false));
    }

    @Test
    void readsASingleDecimalDigitAsTenths() {
        handler.handle(request("1", "99", "$0.5", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request("2", "99", "$4999.50", "2018-01-01T00:00:01Z")))
                .contains(new LoadResponse("2", "99", true));
    }

    @Test
    void readsCentsExactly() {
        handler.handle(request("1", "99", "$4999.99", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request("2", "99", "$0.01", "2018-01-01T00:00:01Z")))
                .contains(new LoadResponse("2", "99", true));
        assertThat(handler.handle(request("3", "99", "$0.01", "2018-01-01T00:00:02Z")))
                .contains(new LoadResponse("3", "99", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2018-01-01", "2018-01-01T00:00:00", "1; DROP TABLE load_attempt", "<?php echo(1) />",
            "2018-01-01T24:00:00Z", "yesterday"})
    void rejectsValuesThatAreNotATimestampWithAnOffset(String time) {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "$1.00", time)))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+999999999-12-31T23:59:59Z", "1969-12-31T23:59:59Z", "9999-12-31T23:59:59-05:00"})
    void rejectsTimestampsOutsideTheSupportedYears(String time) {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "$1.00", time)))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("time must be in the years");
    }

    @Test
    void acceptsTimestampsWithFractionsAndOffsets() {
        assertThat(handler.handle(request("1", "99", "$1.00", "2018-01-01T00:00:00.123+05:30"))).isPresent();
    }

    @Test
    void rejectsUnparseableTime() {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "$1.00", "yesterday")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("time");
    }

    @Test
    void reportsEveryMissingFieldAtOnce() {
        assertThatThrownBy(() -> handler.handle(request(null, null, null, null)))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("customer_id")
                .hasMessageContaining("load_amount")
                .hasMessageContaining("time");
    }

    @Test
    void trimsEveryFieldAndEchoesTheTrimmedIds() {
        Optional<LoadResponse> response =
                handler.handle(request(" 1234 ", "\t99\n", " $123.45 ", " 2018-01-01T00:00:00Z "));

        assertThat(response).contains(new LoadResponse("1234", "99", true));
    }

    @Test
    void idsDifferingOnlyByWhitespaceAreTheSameLoad() {
        handler.handle(request("1", "99", "$10.00", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request(" 1 ", " 99 ", "$10.00", "2018-01-01T00:00:01Z"))).isEmpty();
    }

    @Test
    void idsWithLeadingZerosStayDistinct() {
        handler.handle(request("15", "99", "$10.00", "2018-01-01T00:00:00Z"));

        assertThat(handler.handle(request("015", "99", "$10.00", "2018-01-01T00:00:01Z")))
                .contains(new LoadResponse("015", "99", true));
    }

    @Test
    void acceptsIdsOfExactlyTheMaximumLength() {
        String id = "a".repeat(LoadRequest.MAX_ID_LENGTH);

        assertThat(handler.handle(request(id, id, "$1.00", "2018-01-01T00:00:00Z")))
                .contains(new LoadResponse(id, id, true));
    }

    @Test
    void rejectsIdLongerThanTheMaximum() {
        String tooLong = "a".repeat(LoadRequest.MAX_ID_LENGTH + 1);

        assertThatThrownBy(() -> handler.handle(request(tooLong, "99", "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("id must be at most");
        assertThatThrownBy(() -> handler.handle(request("1", tooLong, "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("customer_id must be at most");
    }

    @ParameterizedTest
    @ValueSource(strings = {"124-by-some_system", "cust.42", "A", "0015", "550e8400-e29b-41d4-a716-446655440000"})
    void acceptsIdsMadeOfLettersDigitsAndSeparators(String id) {
        assertThat(handler.handle(request(id, id, "$1.00", "2018-01-01T00:00:00Z")))
                .contains(new LoadResponse(id, id, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<?php echo(\"blah\") />", "1; DROP TABLE load_attempt", "a b", "a'b", "a\nb", "é", "a/b", "a:b"})
    void rejectsIdsOutsideTheAllowedCharacters(String id) {
        assertThatThrownBy(() -> handler.handle(request(id, "99", "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("id may only contain");
        assertThatThrownBy(() -> handler.handle(request("1", id, "$1.00", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("customer_id may only contain");
    }

    @Test
    void rejectsAmountTooLargeToStore() {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "$1" + "0".repeat(16), "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("load_amount");
    }

    @Test
    void doesNotEchoLongOrMultilineValuesInTheErrorMessage() {
        String hostile = "$" + "x".repeat(500) + "\nINJECTED";

        assertThatThrownBy(() -> handler.handle(request("1", "99", hostile, "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(200).doesNotContain("\n"));
    }

    @Test
    void invalidRequestIsNotStored() {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "oops", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class);

        assertThat(store.stored).isEmpty();
    }
}
