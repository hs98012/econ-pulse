package com.econpulse.mapping.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.application.AutoMapNewsCommand;
import com.econpulse.mapping.application.AutoMapNewsResult;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsIngestionCommand;
import com.econpulse.news.application.NewsIngestionResult;
import com.econpulse.news.application.NewsIngestionService;
import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "econpulse.internal.news-sync.enabled=true",
        "econpulse.news.provider.type=none"
})
@AutoConfigureMockMvc
@Import(Phase3CoreFlowE2ETest.FakeProviderConfig.class)
class Phase3CoreFlowE2ETest extends AbstractIntegrationTest {

    private static final String RELATED_NEWS_PATH = "/api/v1/terms/{termId}/news";

    private final MockMvc mockMvc;
    private final NewsIngestionService ingestionService;
    private final TermNewsAutoMappingService autoMappingService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    Phase3CoreFlowE2ETest(
            MockMvc mockMvc,
            NewsIngestionService ingestionService,
            TermNewsAutoMappingService autoMappingService,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
        this.mockMvc = mockMvc;
        this.ingestionService = ingestionService;
        this.autoMappingService = autoMappingService;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.articleRepository = articleRepository;
        this.mappingRepository = mappingRepository;
    }

    @BeforeEach
    void setUp() {
        mappingRepository.deleteAll();
        articleRepository.deleteAll();
        aliasRepository.deleteAll();
        termRepository.deleteAll();
    }

    @Test
    void ingestsMapsAndReadsRelatedNewsIdempotentlyWithoutRealNetwork() throws Exception {
        EconomicTerm active = termRepository.saveAndFlush(new EconomicTerm(
                "기준금리",
                "기준금리",
                "definition",
                List.of(new EconomicTermAlias("정책금리", "정책금리"))
        ));
        EconomicTerm inactive = new EconomicTerm("환율", "환율", "definition", List.of());
        inactive.deactivate();
        inactive = termRepository.saveAndFlush(inactive);
        NewsIngestionCommand command = new NewsIngestionCommand("경제", 0, 10, NewsSort.RECENCY);

        NewsIngestionResult firstIngestion = ingestionService.ingest(command);
        List<NewsArticle> articles = articleRepository.findAll().stream()
                .sorted(Comparator.comparing(NewsArticle::getPublishedAt).reversed())
                .toList();
        List<AutoMapNewsResult> firstMappings = mapAll(articles);

        assertThat(firstIngestion).isEqualTo(new NewsIngestionResult(3, 3, 0, 0));
        assertThat(articleRepository.count()).isEqualTo(3);
        assertThat(firstMappings).extracting(AutoMapNewsResult::created).containsExactly(1L, 1L, 0L);
        assertThat(mappingRepository.count()).isEqualTo(2);

        mockMvc.perform(get(RELATED_NEWS_PATH, active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("한국은행 기준금리 동결"))
                .andExpect(jsonPath("$.content[0].summary").value("경제 통화정책 결정"))
                .andExpect(jsonPath("$.content[0].sourceUrl")
                        .value("https://example.com/e2e/exact"))
                .andExpect(jsonPath("$.content[0].publishedAt").value("2026-07-16T03:00:00Z"))
                .andExpect(jsonPath("$.content[0].matchType").value("EXACT_NAME"))
                .andExpect(jsonPath("$.content[0].confidenceScore").value(1.0))
                .andExpect(jsonPath("$.content[0].matchedAt").exists())
                .andExpect(jsonPath("$.content[1].title").value("정책금리 조정 전망"))
                .andExpect(jsonPath("$.content[1].matchType").value("ALIAS"))
                .andExpect(jsonPath("$.content[1].confidenceScore").value(0.8))
                .andExpect(jsonPath("$.totalElements").value(2));

        NewsIngestionResult secondIngestion = ingestionService.ingest(command);
        List<AutoMapNewsResult> secondMappings = mapAll(articles);

        assertThat(secondIngestion).isEqualTo(new NewsIngestionResult(3, 0, 0, 3));
        assertThat(secondMappings).extracting(AutoMapNewsResult::skipped).containsExactly(1L, 1L, 0L);
        assertThat(articleRepository.count()).isEqualTo(3);
        assertThat(mappingRepository.count()).isEqualTo(2);
        mockMvc.perform(get(RELATED_NEWS_PATH, active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
        Long inactiveId = inactive.getId();
        mockMvc.perform(get(RELATED_NEWS_PATH, inactiveId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
        assertThat(mappingRepository.countByEconomicTermId(inactiveId)).isZero();
    }

    private List<AutoMapNewsResult> mapAll(List<NewsArticle> articles) {
        return articles.stream()
                .map(article -> autoMappingService.mapNews(new AutoMapNewsCommand(article.getId())))
                .toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfig {

        @Bean
        @Primary
        NewsProvider phase3E2eNewsProvider() {
            return new FakeNewsProvider(List.of(
                    article(
                            "exact",
                            "한국은행 기준금리 동결",
                            "경제 통화정책 결정",
                            "https://example.com/e2e/exact",
                            "2026-07-16T03:00:00Z"
                    ),
                    article(
                            "alias",
                            "정책금리 조정 전망",
                            "경제 시장 분석",
                            "https://example.com/e2e/alias",
                            "2026-07-16T02:00:00Z"
                    ),
                    article(
                            "unmatched",
                            "증시 거래량 증가",
                            "경제 시장 소식",
                            "https://example.com/e2e/unmatched",
                            "2026-07-16T01:00:00Z"
                    )
            ));
        }

        private static NewsProviderArticle article(
                String id,
                String title,
                String summary,
                String sourceUrl,
                String publishedAt
        ) {
            return new NewsProviderArticle(
                    id,
                    title,
                    summary,
                    "E2E Fake News",
                    sourceUrl,
                    Instant.parse(publishedAt)
            );
        }
    }
}
