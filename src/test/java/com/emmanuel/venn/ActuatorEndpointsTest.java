package com.emmanuel.venn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
class ActuatorEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry registry;

    @Test
    void exposesHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void countsAcceptedRejectedAndDuplicateLoadsByOutcome() throws Exception {
        double accepted = count("accepted");
        double rejected = count("rejected");
        double duplicate = count("duplicate");

        postLoad("m1", "$5000.00");
        postLoad("m2", "$0.01");
        postLoad("m2", "$0.01");

        assertThat(count("accepted") - accepted).isEqualTo(1);
        assertThat(count("rejected") - rejected).isEqualTo(1);
        assertThat(count("duplicate") - duplicate).isEqualTo(1);
        assertThat(registry.find("loads.processed").tag("reason", "DAILY_AMOUNT_LIMIT").counter()).isNotNull();
    }

    private double count(String outcome) {
        return registry.find("loads.processed").tag("outcome", outcome).counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private void postLoad(String id, String amount) throws Exception {
        mvc.perform(post("/v1/loads").contentType(MediaType.APPLICATION_JSON).content(
                "{\"id\":\"%s\",\"customer_id\":\"metrics\",\"load_amount\":\"%s\",\"time\":\"2018-01-01T00:00:00Z\"}"
                        .formatted(id, amount)));
    }

    @Test
    void exposesPrometheusScrapeEndpoint() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }
}
