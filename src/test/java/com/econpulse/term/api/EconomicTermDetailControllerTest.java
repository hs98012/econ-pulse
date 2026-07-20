package com.econpulse.term.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.global.api.PageResponse;
import com.econpulse.term.application.EconomicTermDetailFacade;
import com.econpulse.term.application.EconomicTermDetailResult;
import com.econpulse.term.application.EconomicTermService;
import com.econpulse.term.application.TermNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EconomicTermController.class)
class EconomicTermDetailControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private EconomicTermService economicTermService;

    @MockitoBean
    private EconomicTermDetailFacade detailFacade;

    @Autowired
    EconomicTermDetailControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void successfulDetailUsesOrchestrationBoundaryAndKeepsResponseContract() throws Exception {
        when(detailFacade.findByIdAndRecordView(7L)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/terms/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("기준금리"))
                .andExpect(jsonPath("$.definition").value("기준금리 정의"))
                .andExpect(jsonPath("$.aliases[0]").value("정책금리"))
                .andExpect(jsonPath("$.latestNewsCount").value(2))
                .andExpect(jsonPath("$.createdAt").value("2026-07-20T01:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-20T02:00:00Z"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.rank").doesNotExist());

        verify(detailFacade).findByIdAndRecordView(7L);
        verifyNoInteractions(economicTermService);
    }

    @Test
    void failOpenApplicationResultRemains200WithoutRedisDetails() throws Exception {
        when(detailFacade.findByIdAndRecordView(7L)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/terms/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(content().string(not(containsString("Redis"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void missingTermKeeps404Contract() throws Exception {
        when(detailFacade.findByIdAndRecordView(99L)).thenThrow(new TermNotFoundException());

        mockMvc.perform(get("/api/v1/terms/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "text"})
    void invalidIdReturns400BeforeOrchestration(String termId) throws Exception {
        mockMvc.perform(get("/api/v1/terms/" + termId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(detailFacade);
    }

    @Test
    void listAndSearchDoNotUseDetailRecordingOrchestration() throws Exception {
        when(economicTermService.find(null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(economicTermService.find("금리", 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/terms").param("query", "금리"))
                .andExpect(status().isOk());

        verifyNoInteractions(detailFacade);
    }

    private EconomicTermDetailResult detail() {
        return new EconomicTermDetailResult(
                7L,
                "기준금리",
                "기준금리 정의",
                List.of("정책금리"),
                2,
                Instant.parse("2026-07-20T01:00:00Z"),
                Instant.parse("2026-07-20T02:00:00Z")
        );
    }
}
