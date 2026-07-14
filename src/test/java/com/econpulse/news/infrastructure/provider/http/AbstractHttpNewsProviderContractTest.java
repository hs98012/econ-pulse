package com.econpulse.news.infrastructure.provider.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class AbstractHttpNewsProviderContractTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private MockWebServer server;

    protected abstract NewsProvider createProvider(String baseUrl, Duration connectTimeout, Duration readTimeout);

    protected abstract void assertMappedRequest(
            RecordedRequest request,
            String query,
            int page,
            int size,
            NewsSort sort
    );

    protected String successFixture() {
        return "success.json";
    }

    protected String emptyFixture() {
        return "empty.json";
    }

    protected String htmlFixture() {
        return "html-content.json";
    }

    protected String errorFixture() {
        return "error-body.json";
    }

    protected List<String> invalidPayloadFixtures() {
        return List.of(
                "missing-id.json",
                "missing-title.json",
                "missing-source-url.json",
                "missing-published-at.json",
                "blank-title.json",
                "invalid-date.json",
                "malformed.json"
        );
    }

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void mapsRecencyRequestAndSuccessfulResponse() throws Exception {
        enqueue(200, successFixture());

        NewsSearchResult result = provider().search(new NewsSearchQuery("기준 금리+전망", 2, 10, NewsSort.RECENCY));

        assertMappedRequest(server.takeRequest(), "기준 금리+전망", 2, 10, NewsSort.RECENCY);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).hasValue(21);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.articles()).hasSize(1);
        NewsProviderArticle article = result.articles().get(0);
        assertThat(article.providerArticleId()).isEqualTo("external-1");
        assertThat(article.title()).isEqualTo("기준금리 동결");
        assertThat(article.summary()).isEqualTo("통화정책 결정");
        assertThat(article.sourceName()).isEqualTo("Example News");
        assertThat(article.sourceUrl()).isEqualTo("https://news.example.com/articles/1");
        assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-07-14T02:00:00Z"));
    }

    @Test
    void mapsRelevanceSort() throws Exception {
        enqueue(200, emptyFixture());

        provider().search(new NewsSearchQuery("금리", 0, 20, NewsSort.RELEVANCE));

        assertMappedRequest(server.takeRequest(), "금리", 0, 20, NewsSort.RELEVANCE);
    }

    @Test
    void returnsEmptyResult() {
        enqueue(200, emptyFixture());

        NewsSearchResult result = provider().search(new NewsSearchQuery("없음", 0, 20, NewsSort.RECENCY));

        assertThat(result.articles()).isEmpty();
        assertThat(result.totalElements()).hasValue(0);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void sanitizesHtmlEntitiesWhitespaceAndNullableFields() {
        enqueue(200, htmlFixture());

        NewsSearchResult result = provider().search(new NewsSearchQuery("금리", 0, 20, NewsSort.RECENCY));

        NewsProviderArticle htmlArticle = result.articles().get(0);
        assertThat(htmlArticle.title()).isEqualTo("기준금리 동결 \"결정\"");
        assertThat(htmlArticle.summary()).isEqualTo("A&B <전망> '확인' 연속 공백");
        assertThat(htmlArticle.sourceName()).isEqualTo("news.example.com");
        assertThat(result.articles().get(1).summary()).isEmpty();
    }

    @Test
    void invalidPayloadsReturnNonRetryableInvalidResponse() {
        for (String fixture : invalidPayloadFixtures()) {
            enqueue(200, fixture);
            assertProviderError(provider(), NewsProviderErrorType.INVALID_RESPONSE, false);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authenticationStatusIsNonRetryable(int status) {
        enqueue(status, errorFixture());

        assertProviderError(provider(), NewsProviderErrorType.AUTHENTICATION_FAILED, false);
    }

    @Test
    void rateLimitIsRetryable() {
        enqueue(429, errorFixture());

        assertProviderError(provider(), NewsProviderErrorType.RATE_LIMITED, true);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void serverFailuresAreRetryable(int status) {
        enqueue(status, errorFixture());

        assertProviderError(provider(), NewsProviderErrorType.TEMPORARY_FAILURE, true);
    }

    @Test
    void providerBadRequestIsInvalidResponse() {
        enqueue(400, errorFixture());

        assertProviderError(provider(), NewsProviderErrorType.INVALID_RESPONSE, false);
    }

    @Test
    void readTimeoutIsConfiguredAndRetryable() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(NewsProviderContractTestSupport.fixture(successFixture()))
                .setBodyDelay(200, TimeUnit.MILLISECONDS));
        NewsProvider provider = createProvider(server.url("/").toString(), Duration.ofMillis(100), Duration.ofMillis(20));

        assertProviderError(provider, NewsProviderErrorType.TIMEOUT, true);
    }

    @Test
    void connectionFailureIsTemporaryAndRetryable() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        NewsProvider provider = createProvider(
                "http://127.0.0.1:" + unavailablePort,
                Duration.ofMillis(50),
                Duration.ofMillis(50)
        );

        assertProviderError(provider, NewsProviderErrorType.TEMPORARY_FAILURE, true);
    }

    @Test
    void providerSecretsBodiesAndHttpTypesAreNotExposed() {
        enqueue(503, errorFixture());

        assertThatThrownBy(() -> provider().search(query()))
                .isInstanceOfSatisfying(NewsProviderException.class, exception -> {
                    assertThat(exception.getMessage())
                            .doesNotContain("fixture-secret-key")
                            .doesNotContain("provider-internal-stack-trace")
                            .doesNotContain("RestClient")
                            .doesNotContain("ResourceAccessException");
                    assertThat(exception.getCause()).isNull();
                });
    }

    private NewsProvider provider() {
        return createProvider(server.url("/").toString(), CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    private void enqueue(int status, String fixture) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(NewsProviderContractTestSupport.fixture(fixture)));
    }

    private void assertProviderError(NewsProvider provider, NewsProviderErrorType errorType, boolean retryable) {
        assertThatThrownBy(() -> provider.search(query()))
                .isInstanceOfSatisfying(NewsProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(errorType);
                    assertThat(exception.isRetryable()).isEqualTo(retryable);
                });
    }

    private NewsSearchQuery query() {
        return new NewsSearchQuery("기준금리", 0, 20, NewsSort.RECENCY);
    }
}
