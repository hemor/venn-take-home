package com.emmanuel.venn;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void exposesHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void exposesLoadMetricsAfterAnAttempt() throws Exception {
        mvc.perform(post("/v1/loads").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"m1\",\"customer_id\":\"metrics\",\"load_amount\":\"$1.00\",\"time\":\"2018-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/actuator/metrics/loads.processed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("loads.processed"));
    }

    @Test
    void exposesPrometheusScrapeEndpoint() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }
}
