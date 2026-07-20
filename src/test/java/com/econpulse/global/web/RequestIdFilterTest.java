package com.econpulse.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private static final String GENERATED_ID = "generated-request-id-1234";

    private final RequestIdFilter filter = new RequestIdFilter(() -> GENERATED_ID);
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        MDC.clear();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void reusesValidRequestIdInMdcAndResponseThenCleansMdc() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/terms/1");
        request.addHeader(RequestIdFilter.HEADER_NAME, "client-request-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("client-request-1234");
            assertThat(((MockHttpServletResponse) servletResponse).getHeader(RequestIdFilter.HEADER_NAME))
                    .isEqualTo("client-request-1234");
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("client-request-1234");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidRequestIds")
    void generatesIdForMissingOrInvalidValues(String candidate) throws Exception {
        MockHttpServletRequest request = request("GET", "/actuator/health");
        if (candidate != null) {
            request.addHeader(RequestIdFilter.HEADER_NAME, candidate);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(GENERATED_ID));

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(GENERATED_ID);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void restoresPreviousMdcEvenWhenChainThrows() {
        MDC.put(RequestIdFilter.MDC_KEY, "previous-request-id");
        MockHttpServletRequest request = request("GET", "/api/v1/terms/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new ServletException("failure");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("previous-request-id");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void completionLogHasSafeStructuredFieldsWithoutQueryOrHeaders() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/terms");
        request.setQueryString("query=secret-search");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("Cookie", "SESSION=secret-session");
        request.addHeader("X-Naver-Client-Secret", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(400));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        Map<String, Object> fields = fields(event);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, GENERATED_ID);
        assertThat(fields)
                .containsEntry("event", "http_request_completed")
                .containsEntry("method", "GET")
                .containsEntry("path", "/api/v1/terms")
                .containsEntry("status", 400)
                .containsKey("durationMs");
        assertThat(event.toString())
                .doesNotContain("secret-search")
                .doesNotContain("secret-token")
                .doesNotContain("secret-session")
                .doesNotContain("X-Naver-Client-Secret");
    }

    private static java.util.stream.Stream<String> invalidRequestIds() {
        return java.util.stream.Stream.of(
                null,
                "",
                "short",
                "a".repeat(129),
                "invalid$request",
                "INJECTED_LOG=true\nnext-line"
        );
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
