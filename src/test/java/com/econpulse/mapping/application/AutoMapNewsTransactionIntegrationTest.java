package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.MatchedField;
import com.econpulse.mapping.domain.TermMatchCandidate;
import com.econpulse.mapping.domain.TermNewsMatcher;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AutoMapNewsTransactionIntegrationTest extends AbstractIntegrationTest {

    private final TermNewsAutoMappingService autoMappingService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @MockitoBean
    private TermNewsMatcher matcher;

    @Autowired
    AutoMapNewsTransactionIntegrationTest(
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
    void rollsBackEarlierMappingWhenLaterSaveFails() {
        EconomicTerm first = termRepository.saveAndFlush(term("GDP", "gdp"));
        termRepository.saveAndFlush(term("CPI", "cpi"));
        NewsArticle article = articleRepository.saveAndFlush(article());
        AtomicInteger invocation = new AtomicInteger();
        when(matcher.match(any(), any())).thenAnswer(ignored -> {
            if (invocation.getAndIncrement() == 0) {
                return Optional.of(candidate(first.getId(), article.getId()));
            }
            return Optional.of(candidate(Long.MAX_VALUE, article.getId()));
        });

        assertThatThrownBy(() -> autoMappingService.mapNews(new AutoMapNewsCommand(article.getId())))
                .isInstanceOf(com.econpulse.term.application.TermNotFoundException.class);

        assertThat(mappingRepository.count()).isZero();
    }

    private static TermMatchCandidate candidate(Long termId, Long articleId) {
        return new TermMatchCandidate(
                termId,
                articleId,
                MatchType.EXACT_NAME,
                new BigDecimal("1.0000"),
                "gdp",
                MatchedField.TITLE
        );
    }

    private static EconomicTerm term(String name, String normalizedName) {
        return new EconomicTerm(name, normalizedName, "definition", List.of());
    }

    private static NewsArticle article() {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/auto-mapping/rollback");
        return NewsArticle.create(
                "GDP CPI",
                null,
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
    }
}
