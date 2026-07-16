package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TermNewsAutoMappingIntegrationTest extends AbstractIntegrationTest {

    private final TermNewsAutoMappingService autoMappingService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    TermNewsAutoMappingIntegrationTest(
            TermNewsAutoMappingService autoMappingService,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
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
    void persistsExactAndAliasMappingsAndCountsUnmatchedAndInactivePairs() {
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

        TermNewsAutoMappingResult result = autoMappingService.map(
                new TermNewsAutoMappingCommand(List.of(unmatched.getId(), matched.getId()))
        );

        assertThat(result.requestedNewsCount()).isEqualTo(2);
        assertThat(result.processedNewsCount()).isEqualTo(2);
        assertThat(result.activeTermCount()).isEqualTo(3);
        assertThat(result.evaluatedPairCount()).isEqualTo(6);
        assertThat(result.matchedCandidateCount()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.unmatchedPairCount()).isEqualTo(4);
        assertThat(mappingRepository.count()).isEqualTo(2);

        TermNewsMapping exact = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(exactTerm.getId(), matched.getId())
                .orElseThrow();
        assertThat(exact.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(exact.getConfidenceScore()).isEqualByComparingTo("1.0000");
        assertThat(exact.getMatchedAt()).isNotNull();

        TermNewsMapping alias = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(aliasTerm.getId(), matched.getId())
                .orElseThrow();
        assertThat(alias.getMatchType()).isEqualTo(MatchType.ALIAS);
        assertThat(alias.getConfidenceScore()).isEqualByComparingTo("0.8000");
    }

    @Test
    void repeatedCommandIsIdempotent() {
        termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        NewsArticle article = articleRepository.saveAndFlush(article(3, "GDP 전망"));
        TermNewsAutoMappingCommand command = TermNewsAutoMappingCommand.single(article.getId());

        TermNewsAutoMappingResult first = autoMappingService.map(command);
        TermNewsAutoMappingResult second = autoMappingService.map(command);

        assertThat(first.created()).isEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(mappingRepository.count()).isEqualTo(1);
    }

    @Test
    void mapsOneNewsAgainstActiveTermsAndKeepsRowsOnRepeatedExecution() {
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
        NewsArticle article = articleRepository.saveAndFlush(article(4, "GDP와 CPI 상승"));

        AutoMapNewsResult first = autoMappingService.mapNews(new AutoMapNewsCommand(article.getId()));
        AutoMapNewsResult second = autoMappingService.mapNews(new AutoMapNewsCommand(article.getId()));

        assertThat(first).isEqualTo(new AutoMapNewsResult(article.getId(), 3, 2, 2, 0, 0, 1));
        assertThat(second).isEqualTo(new AutoMapNewsResult(article.getId(), 3, 2, 0, 0, 2, 1));
        assertThat(mappingRepository.count()).isEqualTo(2);
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(exact.getId(), article.getId()))
                .get().extracting(TermNewsMapping::getMatchType).isEqualTo(MatchType.EXACT_NAME);
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(alias.getId(), article.getId()))
                .get().extracting(TermNewsMapping::getMatchType).isEqualTo(MatchType.ALIAS);
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(inactive.getId(), article.getId()))
                .isEmpty();
    }

    @Test
    void mapsSummaryButDoesNotPersistAsciiSubstringFalsePositive() {
        EconomicTerm gdp = termRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        EconomicTerm cpi = termRepository.saveAndFlush(term(
                "소비자물가지수",
                "소비자물가지수",
                List.of(new EconomicTermAlias("CPI", "cpi"))
        ));
        NewsArticle article = articleRepository.saveAndFlush(article(5, "Scorpio 전망", "GDP는 상승"));

        AutoMapNewsResult result = autoMappingService.mapNews(new AutoMapNewsCommand(article.getId()));

        assertThat(result).isEqualTo(new AutoMapNewsResult(article.getId(), 2, 1, 1, 0, 0, 1));
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(gdp.getId(), article.getId()))
                .get().extracting(TermNewsMapping::getConfidenceScore)
                .isEqualTo(new java.math.BigDecimal("0.9000"));
        assertThat(mappingRepository.findByEconomicTermIdAndNewsArticleId(cpi.getId(), article.getId()))
                .isEmpty();
    }

    private EconomicTerm term(String name, String normalizedName, List<EconomicTermAlias> aliases) {
        return new EconomicTerm(name, normalizedName, "definition", aliases);
    }

    private NewsArticle article(int sequence, String title) {
        return article(sequence, title, null);
    }

    private NewsArticle article(int sequence, String title, String summary) {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/auto-mapping/" + sequence);
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
