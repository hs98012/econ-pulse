package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TermRelatedNewsQueryIntegrationTest extends AbstractIntegrationTest {

    private final TermRelatedNewsQueryService queryService;
    private final TermNewsAutoMappingService autoMappingService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    TermRelatedNewsQueryIntegrationTest(
            TermRelatedNewsQueryService queryService,
            TermNewsAutoMappingService autoMappingService,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
        this.queryService = queryService;
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
    void readsAutomaticExactAndAliasMappingsOnceInStableNewsOrder() {
        EconomicTerm term = termRepository.saveAndFlush(new EconomicTerm(
                "국내총생산",
                "국내총생산",
                "definition",
                List.of(new EconomicTermAlias("GDP", "gdp"))
        ));
        NewsArticle lowerId = articleRepository.saveAndFlush(article(1, "국내총생산 전망"));
        NewsArticle higherId = articleRepository.saveAndFlush(article(2, "GDP 성장률"));

        autoMappingService.mapNews(new AutoMapNewsCommand(lowerId.getId()));
        autoMappingService.mapNews(new AutoMapNewsCommand(higherId.getId()));
        autoMappingService.mapNews(new AutoMapNewsCommand(lowerId.getId()));
        autoMappingService.mapNews(new AutoMapNewsCommand(higherId.getId()));

        var firstPage = queryService.find(new TermRelatedNewsQuery(term.getId(), 0, 1));
        var secondPage = queryService.find(new TermRelatedNewsQuery(term.getId(), 1, 1));

        assertThat(firstPage.content()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(higherId.getId());
            assertThat(response.matchType()).isEqualTo(MatchType.ALIAS);
            assertThat(response.confidenceScore()).isEqualByComparingTo("0.8000");
            assertThat(response.confidenceScore().scale()).isEqualTo(4);
            assertThat(response.matchedAt()).isNotNull();
        });
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.content()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(lowerId.getId());
            assertThat(response.matchType()).isEqualTo(MatchType.EXACT_NAME);
        });
        assertThat(mappingRepository.count()).isEqualTo(2);
    }

    @Test
    void returnsEmptyPageAndRejectsMissingOrInactiveTerms() {
        EconomicTerm active = termRepository.saveAndFlush(term("GDP"));
        EconomicTerm inactive = term("CPI");
        inactive.deactivate();
        inactive = termRepository.saveAndFlush(inactive);

        var empty = queryService.find(new TermRelatedNewsQuery(active.getId(), 3, 20));

        assertThat(empty.content()).isEmpty();
        assertThat(empty.page()).isEqualTo(3);
        assertThat(empty.totalElements()).isZero();
        Long inactiveId = inactive.getId();
        assertThatThrownBy(() -> queryService.find(new TermRelatedNewsQuery(inactiveId, 0, 20)))
                .isInstanceOf(TermNotFoundException.class);
        assertThatThrownBy(() -> queryService.find(new TermRelatedNewsQuery(Long.MAX_VALUE, 0, 20)))
                .isInstanceOf(TermNotFoundException.class);
    }

    private static EconomicTerm term(String name) {
        return new EconomicTerm(name, name.toLowerCase(), "definition", List.of());
    }

    private static NewsArticle article(int sequence, String title) {
        var url = new NewsUrlHasher().hash("https://example.com/query-integration/" + sequence);
        return NewsArticle.create(
                title,
                "summary",
                "source",
                url.normalizedUrl(),
                url.hash(),
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
    }
}
