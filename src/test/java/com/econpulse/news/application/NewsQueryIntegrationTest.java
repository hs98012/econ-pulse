package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.global.api.PageResponse;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import com.econpulse.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class NewsQueryIntegrationTest extends AbstractIntegrationTest {

    private final NewsArticleRepository repository;
    private final NewsQueryService queryService;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    NewsQueryIntegrationTest(
            NewsArticleRepository repository,
            NewsQueryService queryService,
            MockMvc mockMvc,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.queryService = queryService;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void ingestsThenQueriesMysqlAcrossPageBoundaryAndById() {
        NewsIngestionService ingestionService = new NewsIngestionService(
                new FakeNewsProvider(List.of(
                        article("1", "가장 오래된 뉴스", "2026-07-12T00:00:00Z"),
                        article("2", "같은 시각 낮은 ID", "2026-07-13T00:00:00Z"),
                        article("3", "같은 시각 높은 ID", "2026-07-13T00:00:00Z"),
                        article("4", "가장 최신 뉴스", "2026-07-14T00:00:00Z")
                )),
                repository,
                Clock.fixed(Instant.parse("2026-07-14T01:00:00Z"), ZoneOffset.UTC)
        );
        ingestionService.ingest(new NewsIngestionCommand("뉴스", 0, 10, NewsSort.RECENCY));

        PageResponse<NewsSummaryResponse> firstPage = queryService.findAll(new NewsPageQuery(0, 2));
        PageResponse<NewsSummaryResponse> nextPage = queryService.findAll(new NewsPageQuery(1, 2));

        assertThat(firstPage.content()).extracting(NewsSummaryResponse::title)
                .containsExactly("가장 최신 뉴스", "같은 시각 높은 ID");
        assertThat(nextPage.content()).extracting(NewsSummaryResponse::title)
                .containsExactly("같은 시각 낮은 ID", "가장 오래된 뉴스");
        assertThat(firstPage.totalElements()).isEqualTo(4);

        Long newsId = firstPage.content().get(0).id();
        NewsDetailResponse detail = queryService.findById(newsId);
        assertThat(detail.title()).isEqualTo("가장 최신 뉴스");
        assertThat(detail.publishedAt()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(detail.collectedAt()).isEqualTo(Instant.parse("2026-07-14T01:00:00Z"));

        assertThatThrownBy(() -> queryService.findById(Long.MAX_VALUE))
                .isInstanceOf(NewsNotFoundException.class);
    }

    @Test
    void fakeProviderIngestionIsExposedThroughPublicNewsApi() throws Exception {
        NewsIngestionService ingestionService = new NewsIngestionService(
                new FakeNewsProvider(List.of(
                        article("1", "오래된 뉴스", "2026-07-12T00:00:00Z"),
                        article("2", "동시각 낮은 ID 뉴스", "2026-07-13T00:00:00Z"),
                        article("3", "동시각 높은 ID 뉴스", "2026-07-13T00:00:00Z"),
                        article("4", "최신 뉴스", "2026-07-14T00:00:00Z")
                )),
                repository,
                Clock.fixed(Instant.parse("2026-07-14T01:00:00Z"), ZoneOffset.UTC)
        );
        ingestionService.ingest(new NewsIngestionCommand("뉴스", 0, 10, NewsSort.RECENCY));

        MvcResult firstPage = mockMvc.perform(get("/api/v1/news").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("최신 뉴스"))
                .andExpect(jsonPath("$.content[1].title").value("동시각 높은 ID 뉴스"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn();

        mockMvc.perform(get("/api/v1/news").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("동시각 낮은 ID 뉴스"))
                .andExpect(jsonPath("$.content[1].title").value("오래된 뉴스"));

        long latestId = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("content").get(0).path("id").asLong();
        mockMvc.perform(get("/api/v1/news/{newsId}", latestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("최신 뉴스"))
                .andExpect(jsonPath("$.publishedAt", matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")));

        mockMvc.perform(get("/api/v1/news/{newsId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));
    }

    private NewsProviderArticle article(String id, String title, String publishedAt) {
        return new NewsProviderArticle(
                id,
                title,
                "뉴스 요약",
                "Example News",
                "https://example.com/news/" + id,
                Instant.parse(publishedAt)
        );
    }
}
