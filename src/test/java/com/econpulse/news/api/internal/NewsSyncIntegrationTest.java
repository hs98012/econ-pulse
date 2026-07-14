package com.econpulse.news.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import com.econpulse.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "econpulse.internal.news-sync.enabled=true")
@AutoConfigureMockMvc
@Import(NewsSyncIntegrationTest.ProviderTestConfig.class)
class NewsSyncIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/internal/api/v1/news/sync";

    private final MockMvc mockMvc;
    private final NewsArticleRepository repository;
    private final MutableFakeNewsProvider provider;

    @Autowired
    NewsSyncIntegrationTest(
            MockMvc mockMvc,
            NewsArticleRepository repository,
            MutableFakeNewsProvider provider
    ) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.provider = provider;
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        provider.useArticles(List.of());
    }

    @Test
    void syncIsIdempotentUpdatesChangedArticleAndHandlesEmptyResult() throws Exception {
        provider.useArticles(List.of(
                article("1", "기준금리 동결"),
                article("2", "기준금리 전망")
        ));

        sync("기준금리")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0));
        assertThat(repository.count()).isEqualTo(2);

        sync("기준금리")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(2));
        assertThat(repository.count()).isEqualTo(2);

        provider.useArticles(List.of(
                article("1", "기준금리 인하"),
                article("2", "기준금리 전망")
        ));
        sync("기준금리")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
        assertThat(repository.count()).isEqualTo(2);

        sync("검색결과없음")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(0))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    void providerFailureReturns503AndDoesNotChangeDatabase() throws Exception {
        provider.useArticles(List.of(article("1", "기준금리 동결")));
        sync("기준금리").andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(1);

        provider.failWith(NewsProviderErrorType.TIMEOUT);

        sync("기준금리")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NEWS_PROVIDER_UNAVAILABLE"));
        assertThat(repository.count()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions sync(String query) throws Exception {
        String body = "{\"query\":\"" + query + "\",\"page\":0,\"size\":20,\"sort\":\"RECENCY\"}";
        return mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private NewsProviderArticle article(String id, String title) {
        return new NewsProviderArticle(
                id,
                title,
                "기준금리 기사 요약",
                "Example News",
                "https://example.com/news/" + id,
                Instant.parse("2026-07-14T00:00:00Z")
        );
    }

    @TestConfiguration
    static class ProviderTestConfig {

        @Bean
        MutableFakeNewsProvider newsProvider() {
            return new MutableFakeNewsProvider();
        }
    }

    static class MutableFakeNewsProvider implements NewsProvider {

        private NewsProvider delegate = new FakeNewsProvider(List.of());

        void useArticles(List<NewsProviderArticle> articles) {
            this.delegate = new FakeNewsProvider(articles);
        }

        void failWith(NewsProviderErrorType errorType) {
            this.delegate = query -> {
                throw new NewsProviderException(errorType, "provider secret response");
            };
        }

        @Override
        public NewsSearchResult search(NewsSearchQuery query) {
            return delegate.search(query);
        }
    }
}
