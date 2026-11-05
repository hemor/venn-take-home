package com.emmanuel.venn.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private String filterAndCaptureMdc(MockHttpServletRequest request) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    private static MockHttpServletRequest requestWithHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, value);
        return request;
    }

    @Test
    void generatesIdWhenHeaderIsAbsent() throws Exception {
        String seen = filterAndCaptureMdc(new MockHttpServletRequest());

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seen);
    }

    @Test
    void reusesIncomingId() throws Exception {
        String seen = filterAndCaptureMdc(requestWithHeader("abc-123_X.y"));

        assertThat(seen).isEqualTo("abc-123_X.y");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_X.y");
    }

    @Test
    void replacesIncomingIdThatCouldInjectLogLines() throws Exception {
        String seen = filterAndCaptureMdc(requestWithHeader("abc\nINFO forged log line"));

        assertThat(seen).doesNotContain("\n").isNotEqualTo("abc\nINFO forged log line");
    }

    @Test
    void replacesOverlongIncomingId() throws Exception {
        String seen = filterAndCaptureMdc(requestWithHeader("a".repeat(65)));

        assertThat(seen).hasSizeLessThanOrEqualTo(64).isNotEqualTo("a".repeat(65));
    }

    @Test
    void clearsMdcAfterTheRequest() throws Exception {
        filterAndCaptureMdc(new MockHttpServletRequest());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
