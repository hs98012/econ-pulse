package com.econpulse.term.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.econpulse.popular.application.PopularTermService;
import com.econpulse.popular.application.RecordTermSearchCommand;
import com.econpulse.popular.application.port.PopularTermStoreException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EconomicTermDetailFacadeTest {

    private final EconomicTermService termService = mock(EconomicTermService.class);
    private final PopularTermService popularTermService = mock(PopularTermService.class);
    private final EconomicTermDetailFacade facade = new EconomicTermDetailFacade(termService, popularTermService);

    @Test
    void returnsDetailAndRecordsExactIdAfterSuccessfulQuery() {
        EconomicTermDetailResult detail = detail(7L);
        when(termService.findById(7L)).thenReturn(detail);

        assertThat(facade.findByIdAndRecordView(7L)).isSameAs(detail);

        InOrder order = inOrder(termService, popularTermService);
        order.verify(termService).findById(7L);
        order.verify(popularTermService).recordSearch(new RecordTermSearchCommand(7L));
        order.verifyNoMoreInteractions();
    }

    @Test
    void repeatedDetailRequestsAreRecordedOncePerRequest() {
        when(termService.findById(7L)).thenReturn(detail(7L));

        facade.findByIdAndRecordView(7L);
        facade.findByIdAndRecordView(7L);

        verify(popularTermService, times(2)).recordSearch(new RecordTermSearchCommand(7L));
    }

    @Test
    void queryFailureIsPropagatedWithoutRecording() {
        TermNotFoundException failure = new TermNotFoundException();
        when(termService.findById(99L)).thenThrow(failure);

        assertThatThrownBy(() -> facade.findByIdAndRecordView(99L)).isSameAs(failure);

        verifyNoInteractions(popularTermService);
    }

    @Test
    void redisUnavailableIsLoggedSafelyAndDetailRemainsSuccessful(CapturedOutput output) {
        EconomicTermDetailResult detail = detail(7L);
        when(termService.findById(7L)).thenReturn(detail);
        org.mockito.Mockito.doThrow(new PopularTermStoreException(
                PopularTermStoreException.Reason.UNAVAILABLE,
                "RedisCommandTimeoutException redis://secret-host:6379"
        )).when(popularTermService).recordSearch(new RecordTermSearchCommand(7L));

        MDC.put("requestId", "redis-failure-request-1234");
        try {
            assertThat(facade.findByIdAndRecordView(7L)).isSameAs(detail);
        } finally {
            MDC.remove("requestId");
        }
        assertThat(output.getOut())
                .contains("popular_term_record_failed")
                .contains("economicTermId")
                .contains("redis-failure-request-1234")
                .doesNotContain("RedisCommandTimeoutException")
                .doesNotContain("secret-host");
    }

    @Test
    void invalidStoreDataAndUnexpectedRuntimeFailuresAreNotHidden() {
        when(termService.findById(7L)).thenReturn(detail(7L));
        PopularTermStoreException invalidData = new PopularTermStoreException(
                PopularTermStoreException.Reason.INVALID_DATA,
                "invalid data"
        );
        org.mockito.Mockito.doThrow(invalidData)
                .when(popularTermService).recordSearch(new RecordTermSearchCommand(7L));

        assertThatThrownBy(() -> facade.findByIdAndRecordView(7L)).isSameAs(invalidData);

        RuntimeException unexpected = new RuntimeException("programming error");
        org.mockito.Mockito.doThrow(unexpected)
                .when(popularTermService).recordSearch(new RecordTermSearchCommand(8L));
        when(termService.findById(8L)).thenReturn(detail(8L));

        assertThatThrownBy(() -> facade.findByIdAndRecordView(8L)).isSameAs(unexpected);
    }

    private EconomicTermDetailResult detail(long id) {
        return new EconomicTermDetailResult(
                id,
                "기준금리",
                "기준금리 정의",
                List.of("정책금리"),
                2,
                Instant.parse("2026-07-20T01:00:00Z"),
                Instant.parse("2026-07-20T02:00:00Z")
        );
    }
}
