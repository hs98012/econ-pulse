package com.econpulse.mapping.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "econpulse.internal.term-news-mapping.enabled=true")
@AutoConfigureMockMvc
class AutoTermNewsMappingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/internal/api/v1/news/{newsId}/term-mappings/auto";

    private final MockMvc mockMvc;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    AutoTermNewsMappingControllerIntegrationTest(
            MockMvc mockMvc,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
        this.mockMvc = mockMvc;
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
    void autoMappingCreatesExactAndAliasMappingsAndIsIdempotent() throws Exception {
        EconomicTerm exact = termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        EconomicTerm alias = termRepository.saveAndFlush(term(
                "소비자물가지수",
                "소비자물가지수",
                List.of(new EconomicTermAlias("CPI", "cpi"))
        ));
        termRepository.saveAndFlush(term("환율", "환율", List.of()));
        EconomicTerm inactive = term("실업률", "실업률", List.of());
        inactive.deactivate();
        termRepository.saveAndFlush(inactive);
        NewsArticle article = articleRepository.saveAndFlush(article(1, "GDP와 CPI 상승", null));

        mockMvc.perform(post(PATH, article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsArticleId").value(article.getId()))
                .andExpect(jsonPath("$.evaluatedTerms").value(3))
                .andExpect(jsonPath("$.matchedCandidates").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.noMatch").value(1));
        assertThat(mappingRepository.count()).isEqualTo(2);

        TermNewsMapping exactMapping = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(exact.getId(), article.getId())
                .orElseThrow();
        assertThat(exactMapping.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        TermNewsMapping aliasMapping = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(alias.getId(), article.getId())
                .orElseThrow();
        assertThat(aliasMapping.getMatchType()).isEqualTo(MatchType.ALIAS);
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(inactive.getId(), article.getId()))
                .isEmpty();

        mockMvc.perform(post(PATH, article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.noMatch").value(1));
        assertThat(mappingRepository.count()).isEqualTo(2);
    }

    @Test
    void summaryMatchSucceedsWithoutPersistingAsciiSubstringFalsePositive() throws Exception {
        EconomicTerm gdp = termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        EconomicTerm cpi = termRepository.saveAndFlush(term(
                "소비자물가지수",
                "소비자물가지수",
                List.of(new EconomicTermAlias("CPI", "cpi"))
        ));
        NewsArticle article = articleRepository.saveAndFlush(article(2, "Scorpio 전망", "GDP는 상승"));

        mockMvc.perform(post(PATH, article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedTerms").value(2))
                .andExpect(jsonPath("$.matchedCandidates").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.noMatch").value(1));

        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(gdp.getId(), article.getId()))
                .get().extracting(TermNewsMapping::getConfidenceScore)
                .isEqualTo(new java.math.BigDecimal("0.9000"));
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(cpi.getId(), article.getId()))
                .isEmpty();
    }

    @Test
    void missingNewsReturns404AndNoActiveTermsReturnZeroCounts() throws Exception {
        mockMvc.perform(post(PATH, Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));

        NewsArticle article = articleRepository.saveAndFlush(article(3, "시장 전망", null));
        mockMvc.perform(post(PATH, article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedTerms").value(0))
                .andExpect(jsonPath("$.matchedCandidates").value(0))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.noMatch").value(0));
    }

    private static EconomicTerm term(
            String name,
            String normalizedName,
            List<EconomicTermAlias> aliases
    ) {
        return new EconomicTerm(name, normalizedName, "definition", aliases);
    }

    private static NewsArticle article(int sequence, String title, String summary) {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/single-auto-mapping/" + sequence);
        return NewsArticle.create(
                title,
                summary,
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
    }
}
