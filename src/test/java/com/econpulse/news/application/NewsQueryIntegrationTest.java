package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.global.api.PageResponse;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import com.econpulse.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NewsQueryIntegrationTest extends AbstractIntegrationTest {

    private final NewsArticleRepository repository;
    private final NewsQueryService queryService;

    @Autowired
    NewsQueryIntegrationTest(NewsArticleRepository repository, NewsQueryService queryService) {
        this.repository = repository;
        this.queryService = queryService;
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
