package com.econpulse.news.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeNewsProviderTest extends NewsProviderContractTest {

    @Override
    protected NewsProvider provider(List<NewsProviderArticle> articles) {
        return new FakeNewsProvider(articles);
    }

    @Test
    void searchesByTitle() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("금리", NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("1", "3");
    }

    @Test
    void searchesBySummary() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("소비 둔화", NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("2");
    }

    @Test
    void excludesNonMatchingArticles() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("무역수지", NewsSort.RECENCY));

        assertThat(result.articles()).isEmpty();
        assertThat(result.totalElements()).hasValue(0);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void searchesWithUnicodeWhitespaceAndCaseNormalization() {
        FakeNewsProvider provider = new FakeNewsProvider(List.of(
                article("1", "ＧＤＰ　성장률 반등", "KOREA economy", "2026-07-14T00:00:00Z")
        ));

        NewsSearchResult result = provider.search(new NewsSearchQuery(" gdp   성장률 ", 0, 10, NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("1");
    }

    @Test
    void sortsByRecency() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("금리", NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("1", "3");
    }

    @Test
    void sortsByRelevance() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("금리", NewsSort.RELEVANCE));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("3", "1");
    }

    @Test
    void appliesPaging() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(new NewsSearchQuery("금리", 1, 1, NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("3");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).hasValue(2);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void returnsEmptyPageWhenNoResultExists() {
        FakeNewsProvider provider = new FakeNewsProvider(defaultArticles());

        NewsSearchResult result = provider.search(query("없는검색어", NewsSort.RECENCY));

        assertThat(result.articles()).isEmpty();
    }

    @Test
    void doesNotShareMutableTestDataState() {
        List<NewsProviderArticle> articles = new ArrayList<>(List.of(
                article("1", "환율 상승", "달러 강세", "2026-07-14T00:00:00Z")
        ));
        FakeNewsProvider provider = new FakeNewsProvider(articles);
        articles.add(article("2", "환율 하락", "원화 강세", "2026-07-15T00:00:00Z"));

        NewsSearchResult result = provider.search(query("환율", NewsSort.RECENCY));

        assertThat(result.articles()).extracting(NewsProviderArticle::providerArticleId)
                .containsExactly("1");
    }

    @Test
    void returnsPlainTextAfterCleaningExternalMarkup() {
        FakeNewsProvider provider = new FakeNewsProvider(List.of(
                article("1", "<b>기준금리</b> &quot;동결&quot;", "금리 &amp; 환율", "2026-07-14T00:00:00Z")
        ));

        NewsSearchResult result = provider.search(query("기준금리", NewsSort.RECENCY));

        assertThat(result.articles().get(0).title()).isEqualTo("기준금리 \"동결\"");
        assertThat(result.articles().get(0).summary()).isEqualTo("금리 & 환율");
    }

    private NewsSearchQuery query(String query, NewsSort sort) {
        return new NewsSearchQuery(query, 0, 10, sort);
    }

    private List<NewsProviderArticle> defaultArticles() {
        return List.of(
                article("1", "기준금리 동결", "한국은행 금리 결정", "2026-07-14T00:00:00Z"),
                article("2", "경기침체 우려", "소비 둔화 신호", "2026-07-13T00:00:00Z"),
                article("3", "채권 금리 금리 급등", "시장금리 상승", "2026-07-12T00:00:00Z")
        );
    }

}
