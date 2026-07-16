package com.econpulse.mapping.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
class TermRelatedNewsRepositoryTest extends AbstractIntegrationTest {

    private final TermNewsMappingRepository mappingRepository;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final EntityManager entityManager;

    @Autowired
    TermRelatedNewsRepositoryTest(
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
    void pagesOnlyTermMappingsByPublishedAtAndNewsIdDescendingWithFetchedNews() {
        EconomicTerm term = termRepository.saveAndFlush(term("GDP"));
        EconomicTerm otherTerm = termRepository.saveAndFlush(term("CPI"));
        NewsArticle oldest = articleRepository.saveAndFlush(article(1, "2026-07-12T00:00:00Z"));
        NewsArticle sameTimeLowerId = articleRepository.saveAndFlush(article(2, "2026-07-13T00:00:00Z"));
        NewsArticle sameTimeHigherId = articleRepository.saveAndFlush(article(3, "2026-07-13T00:00:00Z"));
        NewsArticle newest = articleRepository.saveAndFlush(article(4, "2026-07-14T00:00:00Z"));
        NewsArticle other = articleRepository.saveAndFlush(article(5, "2026-07-15T00:00:00Z"));
        mappingRepository.saveAllAndFlush(List.of(
                mapping(term, oldest, MatchType.ALIAS, "0.7000"),
                mapping(term, sameTimeLowerId, MatchType.ALIAS, "0.8000"),
                mapping(term, sameTimeHigherId, MatchType.EXACT_NAME, "0.9000"),
                mapping(term, newest, MatchType.EXACT_NAME, "1.0000"),
                mapping(otherTerm, other, MatchType.EXACT_NAME, "1.0000")
        ));
        entityManager.clear();

        Page<TermNewsMapping> firstPage = mappingRepository
                .findRelatedNewsByEconomicTermId(term.getId(), PageRequest.of(0, 2));
        Page<TermNewsMapping> secondPage = mappingRepository
                .findRelatedNewsByEconomicTermId(term.getId(), PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).extracting(mapping -> mapping.getNewsArticle().getId())
                .containsExactly(newest.getId(), sameTimeHigherId.getId());
        assertThat(secondPage.getContent()).extracting(mapping -> mapping.getNewsArticle().getId())
                .containsExactly(sameTimeLowerId.getId(), oldest.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent().get(0).getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(firstPage.getContent().get(0).getConfidenceScore()).isEqualByComparingTo("1.0000");
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(firstPage.getContent()).allSatisfy(mapping ->
                assertThat(persistence.isLoaded(mapping, "newsArticle")).isTrue());
    }

    @Test
    void returnsEmptyPageWithoutRelatedMappings() {
        EconomicTerm term = termRepository.saveAndFlush(term("GDP"));

        Page<TermNewsMapping> result = mappingRepository
                .findRelatedNewsByEconomicTermId(term.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
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

    private NewsArticle article(int sequence, String publishedAt) {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/related/" + sequence);
        return NewsArticle.create(
                "title-" + sequence,
                "summary",
                "source",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse(publishedAt),
                Instant.parse("2026-07-15T00:00:00Z")
        );
    }
}
