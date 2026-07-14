package com.econpulse.mapping.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class TermNewsMappingRepositoryTest extends AbstractIntegrationTest {

    private static final LocalDateTime MATCHED_AT = LocalDateTime.parse("2026-07-15T03:04:05.123456");

    private final TermNewsMappingRepository mappingRepository;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final EntityManager entityManager;

    @Autowired
    TermNewsMappingRepositoryTest(
            TermNewsMappingRepository mappingRepository,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            EntityManager entityManager
    ) {
        this.mappingRepository = mappingRepository;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.articleRepository = articleRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        mappingRepository.deleteAll();
        articleRepository.deleteAll();
        aliasRepository.deleteAll();
        termRepository.deleteAll();
    }

    @Test
    void savesAndFindsMappingUsingFlywaySchema() {
        EconomicTerm term = termRepository.saveAndFlush(term());
        NewsArticle article = articleRepository.saveAndFlush(article());
        TermNewsMapping saved = mappingRepository.saveAndFlush(mapping(term, article, "0.8"));

        entityManager.clear();
        TermNewsMapping found = mappingRepository
                .findByEconomicTermIdAndNewsArticleId(term.getId(), article.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(found.getConfidenceScore()).isEqualByComparingTo("0.8000").hasScaleOf(4);
        assertThat(found.getMatchedAt()).isEqualTo(MATCHED_AT);
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(found, "economicTerm")).isFalse();
        assertThat(persistence.isLoaded(found, "newsArticle")).isFalse();
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateTermAndArticlePair() {
        EconomicTerm term = termRepository.saveAndFlush(term());
        NewsArticle article = articleRepository.saveAndFlush(article());
        mappingRepository.saveAndFlush(mapping(term, article, "0.7000"));

        assertThatThrownBy(() -> mappingRepository.saveAndFlush(mapping(term, article, "0.9000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TermNewsMapping mapping(EconomicTerm term, NewsArticle article, String score) {
        return new TermNewsMapping(term, article, MatchType.EXACT_NAME, new BigDecimal(score), MATCHED_AT);
    }

    private EconomicTerm term() {
        return new EconomicTerm("GDP", "gdp", "definition", List.of());
    }

    private NewsArticle article() {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/mapping/news/1");
        return NewsArticle.create(
                "title",
                "summary",
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse("2026-07-14T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z")
        );
    }
}
