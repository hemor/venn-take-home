package com.emmanuel.venn;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void documentsTheLoadEndpointAndItsResponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/loads'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/v1/loads'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/v1/loads'].post.responses['400']").exists());
    }

    @Test
    void usesTheSnakeCaseFieldNamesOfTheTask() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.LoadRequest.properties.customer_id").exists())
                .andExpect(jsonPath("$.components.schemas.LoadRequest.properties.load_amount").exists())
                .andExpect(jsonPath("$.components.schemas.LoadResponse.properties.accepted").exists());
    }
}
