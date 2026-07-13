package com.econpulse.news.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

abstract class NewsProviderContractTest {

    protected abstract NewsProvider provider(List<NewsProviderArticle> articles);

    @Test
    void searchesThroughNewsProviderPort() {
        NewsProvider provider = provider(List.of(
                article("1", "기준금리 동결", "한국은행 결정", "2026-07-14T00:00:00Z"),
                article("2", "환율 상승", "달러 강세", "2026-07-13T00:00:00Z")
        ));

        NewsSearchResult result = provider.search(new NewsSearchQuery("기준금리", 0, 10, NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("1");
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).hasValue(1);
        assertThat(result.hasNext()).isFalse();
    }

    protected NewsProviderArticle article(String id, String title, String summary, String publishedAt) {
        return new NewsProviderArticle(
                id,
                title,
                summary,
                "Fake News",
                "https://example.com/news/" + id,
                Instant.parse(publishedAt)
        );
    }
}
