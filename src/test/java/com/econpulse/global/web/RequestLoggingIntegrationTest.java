package com.econpulse.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.econpulse.global.error.GlobalExceptionHandler;
import com.econpulse.term.api.EconomicTermController;
import com.econpulse.term.application.EconomicTermDetailFacade;
import com.econpulse.term.application.EconomicTermService;
import com.econpulse.term.application.TermNotFoundException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EconomicTermController.class)
class RequestLoggingIntegrationTest {

    private final MockMvc mockMvc;
    private final ListAppender<ILoggingEvent> requestAppender = new ListAppender<>();
    private final ListAppender<ILoggingEvent> errorAppender = new ListAppender<>();
    private final Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    private final Logger errorLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @MockitoBean
    private EconomicTermService economicTermService;

    @MockitoBean
    private EconomicTermDetailFacade detailFacade;

    @Autowired
    RequestLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void setUp() {
        requestAppender.start();
        errorAppender.start();
        requestLogger.addAppender(requestAppender);
        errorLogger.addAppender(errorAppender);
    }

    @AfterEach
    void tearDown() {
        requestLogger.detachAppender(requestAppender);
        errorLogger.detachAppender(errorAppender);
        requestAppender.stop();
        errorAppender.stop();
    }

    @Test
    void expected404HasRequestCorrelationWithoutStackTrace() throws Exception {
        when(detailFacade.findByIdAndRecordView(99L)).thenThrow(new TermNotFoundException());

        mockMvc.perform(get("/api/v1/terms/99")
                        .header(RequestIdFilter.HEADER_NAME, "not-found-request-1234"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, "not-found-request-1234"));

        assertThat(errorAppender.list).hasSize(1);
        ILoggingEvent error = errorAppender.list.get(0);
        assertThat(error.getLevel()).isEqualTo(Level.WARN);
        assertThat(error.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, "not-found-request-1234");
        assertThat(error.getThrowableProxy()).isNull();
        assertSingleCompletion("not-found-request-1234", Level.WARN);
    }

    @Test
    void unexpected500HasOneStackTraceAndCorrelatedCompletion() throws Exception {
        when(detailFacade.findByIdAndRecordView(7L)).thenThrow(new RuntimeException("internal-secret"));

        mockMvc.perform(get("/api/v1/terms/7")
                        .header(RequestIdFilter.HEADER_NAME, "server-error-request-1234"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, "server-error-request-1234"));

        assertThat(errorAppender.list).hasSize(1);
        ILoggingEvent error = errorAppender.list.get(0);
        assertThat(error.getLevel()).isEqualTo(Level.ERROR);
        assertThat(error.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, "server-error-request-1234");
        assertThat(error.getThrowableProxy()).isNotNull();
        assertSingleCompletion("server-error-request-1234", Level.ERROR);
        assertThat(requestAppender.list.get(0).getThrowableProxy()).isNull();
    }

    private void assertSingleCompletion(String requestId, Level level) {
        List<ILoggingEvent> completions = requestAppender.list.stream()
                .filter(event -> event.getFormattedMessage().equals("http_request_completed"))
                .toList();
        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).getLevel()).isEqualTo(level);
        assertThat(completions.get(0).getMDCPropertyMap())
                .containsEntry(RequestIdFilter.MDC_KEY, requestId);
    }
}
