package com.econpulse.mapping.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.application.TermNewsAutoMappingCommand;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TermRelatedNewsIntegrationTest extends AbstractIntegrationTest {

    private final MockMvc mockMvc;
    private final TermNewsAutoMappingService autoMappingService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    TermRelatedNewsIntegrationTest(
            MockMvc mockMvc,
            TermNewsAutoMappingService autoMappingService,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
        this.mockMvc = mockMvc;
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
    void returnsOnlyRelatedNewsWithStablePagingAndNoInternalFields() throws Exception {
        EconomicTerm term = termRepository.saveAndFlush(term("GDP"));
        EconomicTerm otherTerm = termRepository.saveAndFlush(term("CPI"));
        NewsArticle lowerId = articleRepository.saveAndFlush(article(1, "2026-07-14T00:00:00Z", "first"));
        NewsArticle higherId = articleRepository.saveAndFlush(article(2, "2026-07-14T00:00:00Z", "second"));
        NewsArticle newest = articleRepository.saveAndFlush(article(3, "2026-07-15T00:00:00Z", "newest"));
        NewsArticle excluded = articleRepository.saveAndFlush(article(4, "2026-07-16T00:00:00Z", "excluded"));
        mappingRepository.saveAllAndFlush(List.of(
                mapping(term, lowerId, MatchType.ALIAS, "0.7000"),
                mapping(term, higherId, MatchType.EXACT_NAME, "0.9000"),
                mapping(term, newest, MatchType.EXACT_NAME, "1.0000"),
                mapping(otherTerm, excluded, MatchType.EXACT_NAME, "1.0000")
        ));

        mockMvc.perform(get("/api/v1/terms/{termId}/news", term.getId())
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.content[0].matchType").value("EXACT_NAME"))
                .andExpect(jsonPath("$.content[0].confidenceScore").value(1.0))
                .andExpect(jsonPath("$.content[1].id").value(higherId.getId()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].sourceUrlHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].mappingId").doesNotExist())
                .andExpect(jsonPath("$.content[0].matchedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/terms/{termId}/news", term.getId())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(lowerId.getId()))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void emptyMissingAndInactiveTermsFollowPublicTermPolicy() throws Exception {
        EconomicTerm empty = termRepository.saveAndFlush(term("GDP"));
        EconomicTerm inactive = term("CPI");
        inactive.deactivate();
        termRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/terms/{termId}/news", empty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/terms/{termId}/news", inactive.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/terms/{termId}/news", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
    }

    @Test
    void autoMappingOutputIsReadableThroughRelatedNewsApi() throws Exception {
        EconomicTerm term = termRepository.saveAndFlush(term("GDP"));
        NewsArticle article = articleRepository.saveAndFlush(article(5, "2026-07-15T00:00:00Z", "GDP 성장"));

        autoMappingService.map(TermNewsAutoMappingCommand.single(article.getId()));

        mockMvc.perform(get("/api/v1/terms/{termId}/news", term.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(article.getId()))
                .andExpect(jsonPath("$.content[0].matchType").value("EXACT_NAME"))
                .andExpect(jsonPath("$.content[0].confidenceScore").value(1.0));
        assertThat(mappingRepository.count()).isEqualTo(1);
    }

    private EconomicTerm term(String name) {
        return new EconomicTerm(name, name.toLowerCase(), "definition", List.of());
    }

    private TermNewsMapping mapping(
            EconomicTerm term,
            NewsArticle article,
            MatchType type,
            String score
    ) {
        return new TermNewsMapping(
                term,
                article,
                type,
                new BigDecimal(score),
                LocalDateTime.parse("2026-07-15T03:00:00")
        );
    }

    private NewsArticle article(int sequence, String publishedAt, String title) {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/related-api/" + sequence);
        return NewsArticle.create(
                title,
                "summary",
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse(publishedAt),
                Instant.parse("2026-07-15T00:00:00Z")
        );
    }
}
