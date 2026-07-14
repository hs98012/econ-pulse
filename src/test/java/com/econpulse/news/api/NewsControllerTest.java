package com.econpulse.news.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.global.api.PageResponse;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.application.NewsPageQuery;
import com.econpulse.news.application.NewsQueryService;
import com.econpulse.news.domain.NewsArticle;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NewsController.class)
class NewsControllerTest {

    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private NewsQueryService newsQueryService;

    @Autowired
    NewsControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void listUsesDefaultPageAndSizeAndReturnsServiceOrder() throws Exception {
        when(newsQueryService.findAll(new NewsPageQuery(0, 20))).thenReturn(pageResponse());

        mockMvc.perform(get("/api/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].title").value("최신 뉴스"))
                .andExpect(jsonPath("$.content[0].summary").value("최신 요약"))
                .andExpect(jsonPath("$.content[0].sourceName").value("Example News"))
                .andExpect(jsonPath("$.content[0].sourceUrl").value("https://example.com/news/2"))
                .andExpect(jsonPath("$.content[0].publishedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.content[1].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].sourceUrlHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].termMappings").doesNotExist())
                .andExpect(jsonPath("$.content[0].collectedAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());

        verify(newsQueryService).findAll(new NewsPageQuery(0, 20));
    }

    @Test
    void listUsesSpecifiedPageAndSize() throws Exception {
        PageResponse<NewsSummaryResponse> response = new PageResponse<>(List.of(summary(1L)), 2, 5, 11, 3);
        when(newsQueryService.findAll(new NewsPageQuery(2, 5))).thenReturn(response);

        mockMvc.perform(get("/api/v1/news").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void emptyListReturns200WithEmptyContent() throws Exception {
        when(newsQueryService.findAll(new NewsPageQuery(0, 20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1"})
    void invalidPageRangeReturns400(String page) throws Exception {
        assertInvalidListRequest("page", page);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101"})
    void invalidSizeRangeReturns400(String size) throws Exception {
        assertInvalidListRequest("size", size);
    }

    @Test
    void nonNumericPageReturns400WithoutInternalMessage() throws Exception {
        assertInvalidListRequest("page", "abc");
    }

    @Test
    void nonNumericSizeReturns400WithoutInternalMessage() throws Exception {
        assertInvalidListRequest("size", "abc");
    }

    @Test
    void detailReturnsAllContractFieldsAsUtc() throws Exception {
        NewsDetailResponse response = new NewsDetailResponse(
                10L,
                "상세 뉴스",
                "상세 요약",
                "Example News",
                "https://example.com/news/10",
                Instant.parse("2026-07-14T02:00:00Z"),
                Instant.parse("2026-07-14T03:00:00Z"),
                Instant.parse("2026-07-14T04:00:00Z"),
                Instant.parse("2026-07-14T05:00:00Z")
        );
        when(newsQueryService.findById(10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/news/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("상세 뉴스"))
                .andExpect(jsonPath("$.summary").value("상세 요약"))
                .andExpect(jsonPath("$.sourceName").value("Example News"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/news/10"))
                .andExpect(jsonPath("$.publishedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.collectedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.createdAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.updatedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.sourceUrlHash").doesNotExist())
                .andExpect(jsonPath("$.termMappings").doesNotExist());
    }

    @Test
    void missingDetailReturns404NewsNotFound() throws Exception {
        when(newsQueryService.findById(999L)).thenThrow(new NewsNotFoundException());

        mockMvc.perform(get("/api/v1/news/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("News article was not found."))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidNewsIdReturns400BeforeService(String newsId) throws Exception {
        mockMvc.perform(get("/api/v1/news/{newsId}", newsId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request."))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verify(newsQueryService, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void controllerDependsOnlyOnQueryServiceAndDoesNotReturnEntity() {
        Field[] fields = NewsController.class.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(NewsQueryService.class);
        assertThat(Arrays.stream(NewsController.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(NewsArticle.class)))
                .isTrue();
    }

    private void assertInvalidListRequest(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/news").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request."))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verifyNoInteractions(newsQueryService);
    }

    private PageResponse<NewsSummaryResponse> pageResponse() {
        return new PageResponse<>(List.of(
                new NewsSummaryResponse(
                        2L,
                        "최신 뉴스",
                        "최신 요약",
                        "Example News",
                        "https://example.com/news/2",
                        Instant.parse("2026-07-14T02:00:00Z")
                ),
                summary(1L)
        ), 0, 20, 2, 1);
    }

    private NewsSummaryResponse summary(Long id) {
        return new NewsSummaryResponse(
                id,
                "이전 뉴스",
                "이전 요약",
                "Example News",
                "https://example.com/news/" + id,
                Instant.parse("2026-07-14T01:00:00Z")
        );
    }
}
