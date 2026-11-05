package com.emmanuel.venn.loads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(LoadController.class)
class LoadControllerTest {

    private static final String BODY = """
            {"id":"1234","customer_id":"99","load_amount":"$123.45","time":"2018-01-01T00:00:00Z"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoadHandler handler;

    private org.springframework.test.web.servlet.ResultActions postLoad(String body) throws Exception {
        return mvc.perform(post("/v1/loads").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void returnsDecisionInTaskFormat() throws Exception {
        when(handler.handle(new LoadRequest("1234", "99", "$123.45", "2018-01-01T00:00:00Z")))
                .thenReturn(Optional.of(new LoadResponse("1234", "99", true)));

        postLoad(BODY)
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":\"1234\",\"customer_id\":\"99\",\"accepted\":true}", true));
    }

    @Test
    void returnsNoContentForRepeatedLoad() throws Exception {
        when(handler.handle(any())).thenReturn(Optional.empty());

        postLoad(BODY)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void returnsProblemDetailForInvalidLoad() throws Exception {
        when(handler.handle(any())).thenThrow(new InvalidLoadException("load_amount must be positive"));

        postLoad(BODY)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid load request"))
                .andExpect(jsonPath("$.detail").value("load_amount must be positive"));
    }

    @Test
    void returnsProblemDetailForMalformedJson() throws Exception {
        postLoad("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void logsMalformedJsonWithoutEchoingTheBody(CapturedOutput output) throws Exception {
        postLoad("{not json SECRET").andExpect(status().isBadRequest());

        assertThat(output).contains("Unreadable load request").doesNotContain("SECRET");
    }

    @Test
    void hidesInternalErrorDetails() throws Exception {
        when(handler.handle(any())).thenThrow(new IllegalStateException("jdbc:h2:mem secret"));

        postLoad(BODY)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.detail").value("Unexpected error while processing the load"));
    }
}
