package com.econpulse.news.infrastructure.provider.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.infrastructure.provider.naver.NaverNewsProperties;
import com.econpulse.news.infrastructure.provider.naver.NaverNewsProvider;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsProviderMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class NaverNewsProviderContractTest extends AbstractHttpNewsProviderContractTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Override
    protected NewsProvider createProvider(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return new NaverNewsProvider(
                new NaverNewsProperties(baseUrl, CLIENT_ID, CLIENT_SECRET, connectTimeout, readTimeout),
                new ObjectMapper().findAndRegisterModules(),
                new MicrometerNewsProviderMetrics(meterRegistry)
        );
    }

    @Override
    protected void assertMappedRequest(
            RecordedRequest request,
            String query,
            int page,
            int size,
            NewsSort sort
    ) {
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).startsWith("/v1/search/news.json?");
        assertThat(request.getHeader("X-Naver-Client-Id")).isEqualTo(CLIENT_ID);
        assertThat(request.getHeader("X-Naver-Client-Secret")).isEqualTo(CLIENT_SECRET);
        HttpUrl url = request.getRequestUrl();
        assertThat(url).isNotNull();
        assertThat(url.queryParameter("query")).isEqualTo(query);
        assertThat(url.queryParameter("start")).isEqualTo(String.valueOf(page * size + 1));
        assertThat(url.queryParameter("display")).isEqualTo(String.valueOf(size));
        assertThat(url.queryParameter("sort")).isEqualTo(sort == NewsSort.RECENCY ? "date" : "sim");
    }

    @Override
    protected String successFixture() {
        return "naver/success.json";
    }

    @Override
    protected String emptyFixture() {
        return "naver/empty.json";
    }

    @Override
    protected String htmlFixture() {
        return "naver/html-content.json";
    }

    @Override
    protected List<String> invalidPayloadFixtures() {
        return List.of(
                "naver/missing-title.json",
                "naver/missing-source-url.json",
                "naver/missing-published-at.json",
                "naver/blank-title.json",
                "naver/invalid-date.json",
                "naver/malformed.json"
        );
    }

    @Override
    protected String expectedProviderArticleId() {
        return "https://news.example.com/articles/1";
    }

    @Override
    protected String expectedSourceName() {
        return "news.example.com";
    }

    @Override
    protected boolean expectedHasNext() {
        return false;
    }

    @Test
    void rejectsStartOverOneThousandBeforeHttpRequest() {
        assertThatThrownBy(() -> contractProvider().search(
                new NewsSearchQuery("금리", 10, 100, NewsSort.RECENCY)
        )).isInstanceOfSatisfying(NewsProviderException.class, exception -> {
            assertThat(exception.getErrorType()).isEqualTo(NewsProviderErrorType.INVALID_REQUEST);
            assertThat(exception.isRetryable()).isFalse();
        });
        assertThat(mockServer().getRequestCount()).isZero();
        assertThat(meterRegistry.find("econpulse.news.provider.requests").meters()).isEmpty();
    }

    @Test
    void recordsOnlyBoundedMetricsForActualSuccessfulAndFailedHttpRequests() {
        enqueueFixture(200, "naver/page-zero.json");
        contractProvider().search(new NewsSearchQuery("비밀 검색어", 0, 10, NewsSort.RECENCY));

        enqueueFixture(401, "error-body.json");
        assertThatThrownBy(() -> contractProvider().search(
                new NewsSearchQuery("다른 검색어", 0, 10, NewsSort.RECENCY)
        )).isInstanceOf(NewsProviderException.class);

        assertThat(meterRegistry.get("econpulse.news.provider.requests")
                .tags("provider", "naver", "outcome", "success", "error", "none")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("econpulse.news.provider.requests")
                .tags("provider", "naver", "outcome", "failure", "error", "authentication")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("econpulse.news.provider.duration").timers())
                .allMatch(timer -> timer.count() == 1);
        assertThat(meterRegistry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains("검색어")
                        || tag.getValue().contains(CLIENT_ID)
                        || tag.getValue().contains(CLIENT_SECRET));
    }

    @Test
    void fallsBackToNaverLinkWhenOriginalLinkIsInvalid() {
        enqueueFixture(200, "naver/link-fallback.json");

        NewsSearchResult result = contractProvider().search(
                new NewsSearchQuery("금리", 0, 20, NewsSort.RECENCY)
        );

        assertThat(result.articles().get(0).sourceUrl()).isEqualTo("https://n.news.naver.com/article/1");
        assertThat(result.articles().get(0).sourceName()).isEqualTo("n.news.naver.com");
    }

    @Test
    void mapsZeroAndOneBasedPagesToNaverStart() throws Exception {
        enqueueFixture(200, "naver/page-zero.json");
        contractProvider().search(new NewsSearchQuery("금리", 0, 10, NewsSort.RECENCY));
        assertThat(mockServer().takeRequest().getRequestUrl().queryParameter("start")).isEqualTo("1");

        enqueueFixture(200, "naver/page-one.json");
        contractProvider().search(new NewsSearchQuery("금리", 1, 10, NewsSort.RECENCY));
        assertThat(mockServer().takeRequest().getRequestUrl().queryParameter("start")).isEqualTo("11");
    }

    @Test
    void derivesHasNextFromItemsTotalAndProviderLimit() {
        enqueueFixture(200, "naver/has-next.json");
        NewsSearchResult available = contractProvider().search(
                new NewsSearchQuery("금리", 0, 2, NewsSort.RECENCY)
        );
        assertThat(available.hasNext()).isTrue();

        enqueueFixture(200, "naver/provider-limit.json");
        NewsSearchResult limited = contractProvider().search(
                new NewsSearchQuery("금리", 99, 10, NewsSort.RECENCY)
        );
        assertThat(limited.hasNext()).isFalse();
    }

    @Test
    void rejectsContradictoryResponseMetadata() {
        enqueueFixture(200, "naver/metadata-mismatch.json");

        assertThatThrownBy(() -> contractProvider().search(
                new NewsSearchQuery("금리", 0, 10, NewsSort.RECENCY)
        )).isInstanceOfSatisfying(NewsProviderException.class, exception ->
                assertThat(exception.getErrorType()).isEqualTo(NewsProviderErrorType.INVALID_RESPONSE));
    }

    @Test
    void errorDoesNotExposeCredentialsOrProviderBody() {
        enqueueFixture(401, "error-body.json");

        assertThatThrownBy(() -> contractProvider().search(
                new NewsSearchQuery("금리", 0, 10, NewsSort.RECENCY)
        )).isInstanceOfSatisfying(NewsProviderException.class, exception -> {
            assertThat(exception.getMessage()).doesNotContain(CLIENT_ID, CLIENT_SECRET);
            assertThat(exception.getMessage()).doesNotContain("fixture-secret-key", "provider-internal-stack-trace");
            assertThat(exception.getMessage()).doesNotContain("RestClient", "WebClient", "OkHttp");
        });
    }
}
