package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.naver.NaverNewsProperties;
import com.econpulse.news.infrastructure.provider.naver.NaverNewsProvider;
import com.econpulse.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NaverNewsIngestionIntegrationTest extends AbstractIntegrationTest {

    private final NewsArticleRepository repository;
    private MockWebServer server;
    private NewsIngestionService service;

    @Autowired
    NaverNewsIngestionIntegrationTest(NewsArticleRepository repository) {
        this.repository = repository;
    }

    @BeforeEach
    void setUp() throws IOException {
        repository.deleteAll();
        server = new MockWebServer();
        server.start();
        NaverNewsProvider provider = new NaverNewsProvider(
                new NaverNewsProperties(
                        server.url("/").toString(),
                        "integration-client-id",
                        "integration-client-secret",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                ),
                new ObjectMapper().findAndRegisterModules()
        );
        service = new NewsIngestionService(
                provider,
                repository,
                Clock.fixed(Instant.parse("2026-07-14T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void naverResponseIsSanitizedStoredIdempotentlyAndUpdated() {
        enqueue("naver/ingestion-success.json", 200);
        NewsIngestionResult first = service.ingest(command());

        assertThat(first).isEqualTo(new NewsIngestionResult(1, 1, 0, 0));
        NewsArticle created = repository.findAll().get(0);
        assertThat(created.getTitle()).isEqualTo("기준금리 \"동결\"");
        assertThat(created.getSummary()).isEqualTo("한국은행 통화정책 결정");
        assertThat(created.getSourceUrl()).isEqualTo("https://news.example.com/articles/1");
        assertThat(created.getPublishedAt()).isEqualTo(LocalDateTime.parse("2026-07-14T02:00:00"));
        assertThat(created.getSourceUrlHash()).hasSize(32);

        enqueue("naver/ingestion-success.json", 200);
        NewsIngestionResult duplicate = service.ingest(command());
        assertThat(duplicate).isEqualTo(new NewsIngestionResult(1, 0, 0, 1));
        assertThat(repository.count()).isOne();

        enqueue("naver/ingestion-updated.json", 200);
        NewsIngestionResult updated = service.ingest(command());
        assertThat(updated).isEqualTo(new NewsIngestionResult(1, 0, 1, 0));
        assertThat(repository.count()).isOne();
        assertThat(repository.findAll().get(0).getTitle()).isEqualTo("기준금리 인하");
    }

    @Test
    void providerFailureDoesNotChangeDatabase() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("sensitive provider failure"));

        assertThatThrownBy(() -> service.ingest(command())).isInstanceOf(NewsProviderException.class);
        assertThat(repository.count()).isZero();
    }

    private NewsIngestionCommand command() {
        return new NewsIngestionCommand("기준금리", 0, 10, NewsSort.RECENCY);
    }

    private void enqueue(String fixture, int status) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(readFixture(fixture)));
    }

    private String readFixture(String fixture) {
        String path = "/news-provider/" + fixture;
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read fixture: " + path, exception);
        }
    }
}
