package com.emmanuel.venn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.domain.InvalidLoadException;
import com.emmanuel.venn.domain.TaskRules;
import com.emmanuel.venn.service.InMemoryLoadAttempts;
import com.emmanuel.venn.service.LoadService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadHandlerTest {

    private final InMemoryLoadAttempts store = new InMemoryLoadAttempts();
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

    @Test
    void rejectsMalformedAmount() {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "12.50", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class)
                .hasMessageContaining("load_amount");
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
    void invalidRequestIsNotStored() {
        assertThatThrownBy(() -> handler.handle(request("1", "99", "oops", "2018-01-01T00:00:00Z")))
                .isInstanceOf(InvalidLoadException.class);

        assertThat(store.stored).isEmpty();
    }
}
