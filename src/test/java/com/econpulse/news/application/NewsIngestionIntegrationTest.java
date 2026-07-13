package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.domain.NewsArticle;
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
class NewsIngestionIntegrationTest extends AbstractIntegrationTest {

    private final NewsArticleRepository newsArticleRepository;

    @Autowired
    NewsIngestionIntegrationTest(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    @BeforeEach
    void setUp() {
        newsArticleRepository.deleteAll();
    }

    @Test
    void fakeProviderIngestionCreatesArticlesInMysql() {
        NewsIngestionService service = service(List.of(
                article("1", "경제 기준금리 동결", "한국은행 결정", "https://example.com/news/1"),
                article("2", "경제 환율 상승", "달러 강세", "https://example.com/news/2")
        ));

        NewsIngestionResult result = service.ingest(command("경제"));

        assertThat(result).isEqualTo(new NewsIngestionResult(2, 2, 0, 0));
        assertThat(newsArticleRepository.count()).isEqualTo(2);
    }

    @Test
    void rerunningSameRequestDoesNotCreateDuplicateRows() {
        List<NewsProviderArticle> articles = List.of(
                article("1", "기준금리 동결", "한국은행 결정", "https://example.com/news/1")
        );
        NewsIngestionService service = service(articles);

        NewsIngestionResult firstResult = service.ingest(command("기준금리"));
        NewsIngestionResult secondResult = service.ingest(command("기준금리"));

        assertThat(firstResult).isEqualTo(new NewsIngestionResult(1, 1, 0, 0));
        assertThat(secondResult).isEqualTo(new NewsIngestionResult(1, 0, 0, 1));
        assertThat(newsArticleRepository.count()).isEqualTo(1);
    }

    @Test
    void changedProviderArticleUpdatesExistingRow() {
        NewsIngestionService firstService = service(List.of(
                article("1", "기준금리 동결", "한국은행 결정", "https://example.com/news/1")
        ));
        NewsIngestionService secondService = service(List.of(
                article("1", "기준금리 인하", "한국은행 결정", "https://example.com/news/1")
        ));

        firstService.ingest(command("기준금리"));
        NewsIngestionResult secondResult = secondService.ingest(command("기준금리"));

        assertThat(secondResult).isEqualTo(new NewsIngestionResult(1, 0, 1, 0));
        assertThat(newsArticleRepository.count()).isEqualTo(1);
        NewsArticle article = newsArticleRepository.findAll().get(0);
        assertThat(article.getTitle()).isEqualTo("기준금리 인하");
    }

    private NewsIngestionService service(List<NewsProviderArticle> articles) {
        return new NewsIngestionService(
                new FakeNewsProvider(articles),
                newsArticleRepository,
                Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private NewsIngestionCommand command(String query) {
        return new NewsIngestionCommand(query, 0, 10, NewsSort.RECENCY);
    }

    private NewsProviderArticle article(String id, String title, String summary, String sourceUrl) {
        return new NewsProviderArticle(
                id,
                title,
                summary,
                "Example News",
                sourceUrl,
                Instant.parse("2026-07-13T00:00:00Z")
        );
    }
}
