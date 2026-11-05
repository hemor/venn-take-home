package com.emmanuel.venn.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static byte[] bytes(int count) {
        return "x".repeat(count).getBytes(StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequest requestWith(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/loads");
        request.setContent(body);
        return request;
    }

    private static MockHttpServletRequest requestOfUnknownLength(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/loads") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }

    @Test
    void passesABodyUpToTheLimitThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWith(bytes(RequestSizeLimitFilter.MAX_BYTES)), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void refusesADeclaredLengthOverTheLimitWithoutReadingIt() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWith(bytes(RequestSizeLimitFilter.MAX_BYTES + 1)), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":413");
    }

    @Test
    void stopsReadingABodyOfUnknownLengthAtTheLimit() throws Exception {
        MockFilterChain readEverything = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException {
                request.getInputStream().readAllBytes();
            }
        };

        assertThatThrownBy(() -> filter.doFilter(
                requestOfUnknownLength(bytes(RequestSizeLimitFilter.MAX_BYTES + 1)), response, readEverything))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void aBodyOfUnknownLengthWithinTheLimitCanBeRead() throws Exception {
        byte[][] read = new byte[1][];
        MockFilterChain readEverything = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException {
                read[0] = request.getInputStream().readAllBytes();
            }
        };

        filter.doFilter(requestOfUnknownLength(bytes(100)), response, readEverything);

        assertThat(read[0]).hasSize(100);
    }
}
