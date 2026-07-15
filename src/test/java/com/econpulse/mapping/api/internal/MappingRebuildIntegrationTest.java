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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "econpulse.internal.mapping-rebuild.enabled=true")
@AutoConfigureMockMvc
class MappingRebuildIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/internal/api/v1/mappings/rebuild";

    private final MockMvc mockMvc;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    MappingRebuildIntegrationTest(
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
    void rebuildCreatesExactAndAliasMappingsCountsUnmatchedAndIsIdempotent() throws Exception {
        EconomicTerm exactTerm = termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        EconomicTerm aliasTerm = termRepository.saveAndFlush(term(
                "소비자물가지수",
                "소비자물가지수",
                List.of(new EconomicTermAlias("CPI", "cpi"))
        ));
        termRepository.saveAndFlush(term("환율", "환율", List.of()));
        EconomicTerm inactive = term("실업률", "실업률", List.of());
        inactive.deactivate();
        termRepository.saveAndFlush(inactive);
        NewsArticle matched = articleRepository.saveAndFlush(article(1, "GDP와 CPI 상승"));
        NewsArticle unmatched = articleRepository.saveAndFlush(article(2, "실업률 전망"));

        rebuild(List.of(unmatched.getId(), matched.getId(), matched.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedNewsCount").value(2))
                .andExpect(jsonPath("$.processedNewsCount").value(2))
                .andExpect(jsonPath("$.activeTermCount").value(3))
                .andExpect(jsonPath("$.evaluatedPairCount").value(6))
                .andExpect(jsonPath("$.matchedCandidateCount").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.unmatchedPairCount").value(4));
        assertThat(mappingRepository.count()).isEqualTo(2);

        TermNewsMapping exact = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(exactTerm.getId(), matched.getId())
                .orElseThrow();
        assertThat(exact.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(exact.getConfidenceScore()).isEqualByComparingTo("1.0000");

        TermNewsMapping alias = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(aliasTerm.getId(), matched.getId())
                .orElseThrow();
        assertThat(alias.getMatchType()).isEqualTo(MatchType.ALIAS);
        assertThat(alias.getConfidenceScore()).isEqualByComparingTo("0.8000");

        rebuild(List.of(matched.getId(), unmatched.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.unmatchedPairCount").value(4));
        assertThat(mappingRepository.count()).isEqualTo(2);
    }

    @Test
    void missingNewsFailsBeforeCreatingAnyMapping() throws Exception {
        termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        NewsArticle article = articleRepository.saveAndFlush(article(3, "GDP 전망"));

        rebuild(List.of(article.getId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));

        assertThat(mappingRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions rebuild(List<Long> ids) throws Exception {
        String body = ids.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{\"newsArticleIds\":[", "]}"));
        return mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private EconomicTerm term(String name, String normalizedName, List<EconomicTermAlias> aliases) {
        return new EconomicTerm(name, normalizedName, "definition", aliases);
    }

    private NewsArticle article(int sequence, String title) {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/rebuild/" + sequence);
        return NewsArticle.create(
                title,
                null,
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
    }
}
