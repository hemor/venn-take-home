package com.emmanuel.venn;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class LoadApiTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions load(String id, String customerId, String amount) throws Exception {
        String body = """
                {"id":"%s","customer_id":"%s","load_amount":"%s","time":"2018-01-01T00:00:00Z"}"""
                .formatted(id, customerId, amount);
        return mvc.perform(post("/v1/loads").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void acceptsThenDeclinesOverTheDailyLimitThenIgnoresARepeat() throws Exception {
        load("a1", "api-1", "$5000.00").andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        load("a2", "api-1", "$0.01").andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        load("a2", "api-1", "$0.01").andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void rejectsInvalidRequestsWithProblemDetailAndStoresNothing() throws Exception {
        load("<script>", "api-2", "$1.00")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("id may only contain letters, digits, '.', '_' and '-'"));
        load("a3", "api-2", "$1,000.00").andExpect(status().isBadRequest());

        load("a3", "api-2", "$1.00").andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void rejectsIdsLongerThanTheColumnInsteadOfFailingInTheDatabase() throws Exception {
        load("x".repeat(65), "api-3", "$1.00").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTimestampsTooFarOutToStoreOrWindow() throws Exception {
        String body = """
                {"id":"far","customer_id":"api-4","load_amount":"$1.00","time":"+999999999-12-31T23:59:59Z"}""";

        mvc.perform(post("/v1/loads").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
