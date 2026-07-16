package com.econpulse.mapping.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.global.api.PageResponse;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import com.econpulse.mapping.application.TermNewsMappingService;
import com.econpulse.mapping.application.TermRelatedNewsQuery;
import com.econpulse.mapping.application.TermRelatedNewsQueryService;
import com.econpulse.mapping.application.TermRelatedNewsResponse;
import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMatcher;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TermRelatedNewsController.class)
class TermRelatedNewsControllerTest {

    private static final String PATH = "/api/v1/terms/{termId}/news";
    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private TermRelatedNewsQueryService queryService;

    @Autowired
    TermRelatedNewsControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void defaultPagingReturnsRelatedNewsFieldsInServiceOrder() throws Exception {
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(1L, 0, 20);
        when(queryService.find(query)).thenReturn(new PageResponse<>(
                List.of(response(31L, MatchType.EXACT_NAME, "1.0000"),
                        response(30L, MatchType.ALIAS, "0.8000")),
                0,
                20,
                2,
                1
        ));

        mockMvc.perform(get(PATH, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(31))
                .andExpect(jsonPath("$.content[0].title").value("title-31"))
                .andExpect(jsonPath("$.content[0].summary").value("summary"))
                .andExpect(jsonPath("$.content[0].sourceName").value("source"))
                .andExpect(jsonPath("$.content[0].sourceUrl").value("https://example.com/31"))
                .andExpect(jsonPath("$.content[0].publishedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.content[0].matchType").value("EXACT_NAME"))
                .andExpect(jsonPath("$.content[0].confidenceScore").value(1.0))
                .andExpect(content().string(containsString("\"confidenceScore\":1.0000")))
                .andExpect(jsonPath("$.content[1].id").value(30))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].sourceUrlHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].mappingId").doesNotExist())
                .andExpect(jsonPath("$.content[0].matchedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.content[0].economicTerm").doesNotExist());

        verify(queryService).find(query);
    }

    @Test
    void explicitPagingAndEmptyPageReturn200() throws Exception {
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(1L, 2, 5);
        when(queryService.find(query)).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get(PATH, 1L).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void missingOrInactiveTermReturns404() throws Exception {
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(99L, 0, 20);
        when(queryService.find(query)).thenThrow(new TermNotFoundException());

        mockMvc.perform(get(PATH, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void invalidTermIdReturns400(String termId) throws Exception {
        assertInvalid(get("/api/v1/terms/" + termId + "/news"));
    }

    @Test
    void invalidPageAndSizeReturn400() throws Exception {
        assertInvalid(get(PATH, 1L).param("page", "-1"));
        assertInvalid(get(PATH, 1L).param("page", "text"));
        assertInvalid(get(PATH, 1L).param("size", "0"));
        assertInvalid(get(PATH, 1L).param("size", "-1"));
        assertInvalid(get(PATH, 1L).param("size", "101"));
        assertInvalid(get(PATH, 1L).param("size", "text"));
    }

    @Test
    void controllerDependsOnlyOnQueryService() {
        Field[] fields = TermRelatedNewsController.class.getDeclaredFields();

        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(TermRelatedNewsQueryService.class);
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(
                EconomicTermRepository.class,
                NewsArticleRepository.class,
                TermNewsMappingRepository.class,
                TermNewsMappingService.class,
                TermNewsMatcher.class,
                TermNewsAutoMappingService.class
        );
    }

    private void assertInvalid(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
        verifyNoInteractions(queryService);
    }

    private TermRelatedNewsResponse response(Long id, MatchType type, String score) {
        return new TermRelatedNewsResponse(
                id,
                "title-" + id,
                "summary",
                "source",
                "https://example.com/" + id,
                Instant.parse("2026-07-15T02:00:00Z"),
                type,
                new BigDecimal(score),
                Instant.parse("2026-07-15T03:00:00Z")
        );
    }
}
