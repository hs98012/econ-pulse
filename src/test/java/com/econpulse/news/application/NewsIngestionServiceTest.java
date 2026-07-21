package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsIngestionMetrics;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NewsIngestionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);

    private final NewsProvider newsProvider = Mockito.mock(NewsProvider.class);
    private final NewsArticleRepository newsArticleRepository = Mockito.mock(NewsArticleRepository.class);
    private final NewsIngestionService newsIngestionService = new NewsIngestionService(
            newsProvider,
            newsArticleRepository,
            FIXED_CLOCK
    );

    @Test
    void savesNewArticleFromProvider() {
        givenProviderReturns(article("1", "기준금리 동결", "요약", "https://example.com/news/1"));
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.empty());

        NewsIngestionResult result = newsIngestionService.ingest(command("기준금리"));

        assertThat(result).isEqualTo(new NewsIngestionResult(1, 1, 0, 0));
        verify(newsArticleRepository).saveAndFlush(any(NewsArticle.class));
    }

    @Test
    void savesMultipleNewArticles() {
        givenProviderReturns(
                article("1", "기준금리 동결", "요약", "https://example.com/news/1"),
                article("2", "환율 상승", "요약", "https://example.com/news/2")
        );
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.empty());

        NewsIngestionResult result = newsIngestionService.ingest(command("경제"));

        assertThat(result).isEqualTo(new NewsIngestionResult(2, 2, 0, 0));
    }

    @Test
    void reingestingSameArticleDoesNotCreateDuplicate() {
        NewsProviderArticle article = article("1", "기준금리 동결", "요약", "https://example.com/news/1");
        givenProviderReturns(article);
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.of(existing(article)));

        NewsIngestionResult result = newsIngestionService.ingest(command("기준금리"));

        assertThat(result).isEqualTo(new NewsIngestionResult(1, 0, 0, 1));
        verify(newsArticleRepository).saveAndFlush(any(NewsArticle.class));
    }

    @Test
    void updatesExistingArticleWhenTitleChanges() {
        NewsProviderArticle changed = article("1", "기준금리 인하", "요약", "https://example.com/news/1");
        NewsArticle existing = existing(article("1", "기준금리 동결", "요약", "https://example.com/news/1"));
        givenProviderReturns(changed);
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.of(existing));

        NewsIngestionResult result = newsIngestionService.ingest(command("기준금리"));

        assertThat(result).isEqualTo(new NewsIngestionResult(1, 0, 1, 0));
        assertThat(existing.getTitle()).isEqualTo("기준금리 인하");
    }

    @Test
    void blankSummaryDoesNotOverwriteExistingSummary() {
        NewsProviderArticle changed = article("1", "기준금리 동결", "", "https://example.com/news/1");
        NewsArticle existing = existing(article("1", "기준금리 동결", "정상 요약", "https://example.com/news/1"));
        givenProviderReturns(changed);
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.of(existing));

        NewsIngestionResult result = newsIngestionService.ingest(command("기준금리"));

        assertThat(result).isEqualTo(new NewsIngestionResult(1, 0, 0, 1));
        assertThat(existing.getSummary()).isEqualTo("정상 요약");
    }

    @Test
    void duplicateUrlInSameProviderResponseUsesFirstArticleAndSkipsLaterDuplicate() {
        givenProviderReturns(
                article("1", "첫 기사", "요약", "https://example.com/news/1"),
                article("2", "중복 기사", "요약", "https://example.com/news/1#fragment")
        );
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.empty());

        NewsIngestionResult result = newsIngestionService.ingest(command("기사"));

        assertThat(result).isEqualTo(new NewsIngestionResult(2, 1, 0, 1));
    }

    @Test
    void trimsUrlAndRemovesFragmentBeforeDuplicateCheck() {
        givenProviderReturns(
                article("1", "첫 기사", "요약", " https://example.com/news/1#fragment "),
                article("2", "중복 기사", "요약", "https://example.com/news/1")
        );
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.empty());

        NewsIngestionResult result = newsIngestionService.ingest(command("기사"));

        assertThat(result).isEqualTo(new NewsIngestionResult(2, 1, 0, 1));
    }

    @Test
    void differentQueryParametersAreNotTreatedAsSameArticle() {
        givenProviderReturns(
                article("1", "첫 기사", "요약", "https://example.com/news/1?a=1"),
                article("2", "둘째 기사", "요약", "https://example.com/news/1?a=2")
        );
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.empty());

        NewsIngestionResult result = newsIngestionService.ingest(command("기사"));

        assertThat(result).isEqualTo(new NewsIngestionResult(2, 2, 0, 0));
    }

    @Test
    void usesFixedClockForCollectedAt() {
        NewsProviderArticle article = article("1", "기준금리 동결", "요약", "https://example.com/news/1");
        NewsArticle existing = existing(article);
        givenProviderReturns(article);
        when(newsArticleRepository.findBySourceUrlHash(any(byte[].class))).thenReturn(Optional.of(existing));

        newsIngestionService.ingest(command("기준금리"));

        assertThat(existing.getCollectedAt()).isEqualTo(LocalDateTime.parse("2026-07-14T00:00:00"));
    }

    @Test
    void providerErrorDoesNotSaveArticle() {
        when(newsProvider.search(any(NewsSearchQuery.class)))
                .thenThrow(new NewsProviderException(NewsProviderErrorType.TIMEOUT, "provider timeout"));

        assertThatThrownBy(() -> newsIngestionService.ingest(command("기준금리")))
                .isInstanceOf(NewsProviderException.class)
                .hasMessage("provider timeout");
        verify(newsArticleRepository, never()).saveAndFlush(any(NewsArticle.class));
    }

    @Test
    void recordsApplicationBoundarySuccessAndFailureWithoutChangingResults() {
        NewsIngestionMetrics metrics = Mockito.mock(NewsIngestionMetrics.class);
        NewsIngestionMetrics.Run successRun = Mockito.mock(NewsIngestionMetrics.Run.class);
        NewsIngestionMetrics.Run failureRun = Mockito.mock(NewsIngestionMetrics.Run.class);
        when(metrics.start()).thenReturn(successRun, failureRun);
        NewsIngestionService measured = new NewsIngestionService(
                newsProvider,
                newsArticleRepository,
                new NewsUrlHasher(),
                FIXED_CLOCK,
                metrics
        );
        givenProviderReturns();

        NewsIngestionResult result = measured.ingest(command("기준금리"));
        NewsProviderException failure = new NewsProviderException(NewsProviderErrorType.TIMEOUT, "timeout");
        when(newsProvider.search(any())).thenThrow(failure);

        assertThat(result).isEqualTo(new NewsIngestionResult(0, 0, 0, 0));
        assertThatThrownBy(() -> measured.ingest(command("기준금리"))).isSameAs(failure);
        verify(successRun).success(result);
        verify(failureRun).failure();
    }

    @Test
    void emptySearchResultReturnsZeroCounts() {
        givenProviderReturns();

        NewsIngestionResult result = newsIngestionService.ingest(command("기준금리"));

        assertThat(result).isEqualTo(new NewsIngestionResult(0, 0, 0, 0));
    }

    private void givenProviderReturns(NewsProviderArticle... articles) {
        when(newsProvider.search(any(NewsSearchQuery.class))).thenReturn(new NewsSearchResult(
                List.of(articles),
                0,
                10,
                OptionalLong.of(articles.length),
                false
        ));
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

    private NewsArticle existing(NewsProviderArticle article) {
        NewsUrl newsUrl = new NewsUrlHasher().hash(article.sourceUrl());
        return NewsArticle.create(
                article.title(),
                article.summary(),
                article.sourceName(),
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                article.publishedAt(),
                Instant.parse("2026-07-13T01:00:00Z")
        );
    }
}
