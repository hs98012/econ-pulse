package com.econpulse.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.mapping.application.AutoMapNewsResult;
import com.econpulse.mapping.infrastructure.metrics.MicrometerTermNewsMappingMetrics;
import com.econpulse.news.application.NewsIngestionResult;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsIngestionMetrics;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsProviderMetrics;
import com.econpulse.popular.application.PopularTermMetrics;
import com.econpulse.popular.infrastructure.metrics.MicrometerPopularTermMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OperationalMetricsTest {

    private static final Set<String> FORBIDDEN_TAGS = Set.of(
            "requestId", "economicTermId", "newsArticleId", "query", "url",
            "exception", "message", "date", "limit", "path"
    );

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void ingestionRecordsSuccessFailureDurationsAndOnlySuccessfulArticleResults() {
        MicrometerNewsIngestionMetrics metrics = new MicrometerNewsIngestionMetrics(registry);
        metrics.start().success(new NewsIngestionResult(10, 3, 2, 5));
        metrics.start().failure();

        assertCounter("econpulse.news.ingestion.runs", "outcome", "success", 1);
        assertCounter("econpulse.news.ingestion.runs", "outcome", "failure", 1);
        assertTimer("econpulse.news.ingestion.duration", "outcome", "success", 1);
        assertTimer("econpulse.news.ingestion.duration", "outcome", "failure", 1);
        assertCounter("econpulse.news.ingestion.articles", "result", "fetched", 10);
        assertCounter("econpulse.news.ingestion.articles", "result", "created", 3);
        assertCounter("econpulse.news.ingestion.articles", "result", "updated", 2);
        assertCounter("econpulse.news.ingestion.articles", "result", "skipped", 5);
    }

    @Test
    void providerUsesBoundedErrorClassificationAndDurationOutcomes() {
        MicrometerNewsProviderMetrics metrics = new MicrometerNewsProviderMetrics(registry);
        metrics.startRequest().success();
        for (NewsProviderErrorType type : NewsProviderErrorType.values()) {
            metrics.startRequest().failure(type);
        }

        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "success", "error", "none");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "authentication");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "rate_limit");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "timeout");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "temporary_failure");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "bad_response");
        assertCounterWithTags("econpulse.news.provider.requests", 1,
                "provider", "naver", "outcome", "failure", "error", "invalid_request");
        assertThat(registry.find("econpulse.news.provider.duration").timers())
                .extracting(timer -> timer.getId().getTag("outcome"))
                .containsExactlyInAnyOrder("success", "failure");
    }

    @Test
    void mappingRecordsSuccessfulResultsAndFailureWithoutPartialResults() {
        MicrometerTermNewsMappingMetrics metrics = new MicrometerTermNewsMappingMetrics(registry);
        metrics.start().success(new AutoMapNewsResult(7L, 10, 6, 2, 1, 3, 4));
        metrics.start().failure();

        assertCounter("econpulse.term_news.mapping.runs", "outcome", "success", 1);
        assertCounter("econpulse.term_news.mapping.runs", "outcome", "failure", 1);
        assertTimer("econpulse.term_news.mapping.duration", "outcome", "success", 1);
        assertTimer("econpulse.term_news.mapping.duration", "outcome", "failure", 1);
        assertCounter("econpulse.term_news.mapping.results", "result", "created", 2);
        assertCounter("econpulse.term_news.mapping.results", "result", "updated", 1);
        assertCounter("econpulse.term_news.mapping.results", "result", "skipped", 3);
        assertCounter("econpulse.term_news.mapping.results", "result", "no_match", 4);
    }

    @Test
    void popularMetricsSeparateRecordAndQueryOutcomes() {
        MicrometerPopularTermMetrics metrics = new MicrometerPopularTermMetrics(registry);
        metrics.recordSucceeded();
        metrics.recordUnavailable();
        PopularTermMetrics.Query success = metrics.startQuery();
        success.success();
        metrics.startQuery().unavailable();
        metrics.startQuery().failure();

        assertCounter("econpulse.popular_term.record", "outcome", "success", 1);
        assertCounter("econpulse.popular_term.record", "outcome", "unavailable", 1);
        assertCounter("econpulse.popular_term.query", "outcome", "success", 1);
        assertCounter("econpulse.popular_term.query", "outcome", "unavailable", 1);
        assertCounter("econpulse.popular_term.query", "outcome", "failure", 1);
        assertTimer("econpulse.popular_term.query.duration", "outcome", "success", 1);
        assertTimer("econpulse.popular_term.query.duration", "outcome", "unavailable", 1);
        assertTimer("econpulse.popular_term.query.duration", "outcome", "failure", 1);
    }

    @Test
    void customMetersHaveOnlyBoundedTagKeys() {
        ingestionRecordsSuccessFailureDurationsAndOnlySuccessfulArticleResults();
        providerUsesBoundedErrorClassificationAndDurationOutcomes();
        mappingRecordsSuccessfulResultsAndFailureWithoutPartialResults();
        popularMetricsSeparateRecordAndQueryOutcomes();

        assertThat(registry.getMeters())
                .filteredOn(meter -> meter.getId().getName().startsWith("econpulse."))
                .flatExtracting(meter -> meter.getId().getTags())
                .noneMatch(tag -> FORBIDDEN_TAGS.contains(tag.getKey()));
    }

    @Test
    void concurrentPopularRecordMetricsDoNotLoseSuccessfulCallsOrConflictOnRegistration() throws Exception {
        MicrometerPopularTermMetrics metrics = new MicrometerPopularTermMetrics(registry);
        int calls = 20;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int index = 0; index < calls; index++) {
            executor.submit(metrics::recordSucceeded);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertCounter("econpulse.popular_term.record", "outcome", "success", calls);
        assertThat(registry.find("econpulse.popular_term.record").counters()).hasSize(1);
    }

    private void assertCounter(String name, String tag, String value, double expected) {
        assertThat(registry.get(name).tag(tag, value).counter().count()).isEqualTo(expected);
    }

    private void assertCounterWithTags(String name, double expected, String... tags) {
        assertThat(registry.get(name).tags(tags).counter().count()).isEqualTo(expected);
    }

    private void assertTimer(String name, String tag, String value, long expected) {
        assertThat(registry.get(name).tag(tag, value).timer().count()).isEqualTo(expected);
    }
}
