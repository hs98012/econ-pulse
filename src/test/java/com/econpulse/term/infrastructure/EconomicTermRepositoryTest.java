package com.econpulse.term.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.domain.TermStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@SpringBootTest
class EconomicTermRepositoryTest extends AbstractIntegrationTest {

    private final EconomicTermRepository economicTermRepository;
    private final EconomicTermAliasRepository economicTermAliasRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final TermNewsMappingRepository termNewsMappingRepository;
    private final EntityManager entityManager;

    @Autowired
    EconomicTermRepositoryTest(
            EconomicTermRepository economicTermRepository,
            EconomicTermAliasRepository economicTermAliasRepository,
            NewsArticleRepository newsArticleRepository,
            TermNewsMappingRepository termNewsMappingRepository,
            EntityManager entityManager
    ) {
        this.economicTermRepository = economicTermRepository;
        this.economicTermAliasRepository = economicTermAliasRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.termNewsMappingRepository = termNewsMappingRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        termNewsMappingRepository.deleteAll();
        newsArticleRepository.deleteAll();
        economicTermAliasRepository.deleteAll();
        economicTermRepository.deleteAll();
    }

    @Test
    void normalizedNameIsUnique() {
        economicTermRepository.saveAndFlush(term("GDP", "gdp", List.of()));

        assertThatThrownBy(() -> economicTermRepository.saveAndFlush(term("gdp", "gdp", List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void normalizedAliasIsUnique() {
        economicTermRepository.saveAndFlush(term("GDP", "gdp", List.of(alias("국내총생산", "국내총생산"))));

        assertThatThrownBy(() -> economicTermRepository.saveAndFlush(term("CPI", "cpi", List.of(alias("국내총생산", "국내총생산")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchesByName() {
        economicTermRepository.saveAndFlush(term("기준금리", "기준금리", List.of()));

        Page<EconomicTerm> result = economicTermRepository.searchByNormalizedNameOrAlias(
                "기준",
                TermStatus.ACTIVE,
                pageable()
        );

        assertThat(result.getContent()).extracting(EconomicTerm::getName).containsExactly("기준금리");
    }

    @Test
    void searchesByAlias() {
        economicTermRepository.saveAndFlush(term("기준금리", "기준금리", List.of(alias("정책금리", "정책금리"))));

        Page<EconomicTerm> result = economicTermRepository.searchByNormalizedNameOrAlias(
                "정책",
                TermStatus.ACTIVE,
                pageable()
        );

        assertThat(result.getContent()).extracting(EconomicTerm::getName).containsExactly("기준금리");
    }

    @Test
    void removesDuplicateSearchResults() {
        economicTermRepository.saveAndFlush(term("기준금리", "기준금리", List.of(alias("정책금리", "정책금리"))));

        Page<EconomicTerm> result = economicTermRepository.searchByNormalizedNameOrAlias(
                "금리",
                TermStatus.ACTIVE,
                pageable()
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void pagesResultsWithStableOrder() {
        economicTermRepository.saveAndFlush(term("B", "b", List.of()));
        economicTermRepository.saveAndFlush(term("A", "a", List.of()));
        economicTermRepository.saveAndFlush(term("C", "c", List.of()));

        Page<EconomicTerm> result = economicTermRepository.findAllByStatusWithAliases(
                TermStatus.ACTIVE,
                PageRequest.of(0, 2, Sort.by("name").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(result.getContent()).extracting(EconomicTerm::getName).containsExactly("A", "B");
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void returnsOnlyActiveTerms() {
        EconomicTerm inactive = term("GDP", "gdp", List.of());
        inactive.deactivate();
        economicTermRepository.saveAndFlush(inactive);
        economicTermRepository.saveAndFlush(term("CPI", "cpi", List.of()));

        Page<EconomicTerm> result = economicTermRepository.findAllByStatusWithAliases(TermStatus.ACTIVE, pageable());

        assertThat(result.getContent()).extracting(EconomicTerm::getName).containsExactly("CPI");
    }

    @Test
    void loadsOnlyActiveTermsWithAliasesInStableIdOrder() {
        EconomicTerm first = economicTermRepository.saveAndFlush(term(
                "GDP", "gdp", List.of(
                        alias("국내총생산", "국내총생산"),
                        alias("국민총생산지표", "국민총생산지표")
                )
        ));
        EconomicTerm second = economicTermRepository.saveAndFlush(term(
                "CPI", "cpi", List.of(alias("소비자물가", "소비자물가"))
        ));
        EconomicTerm withoutAlias = economicTermRepository.saveAndFlush(term("환율", "환율", List.of()));
        EconomicTerm inactive = term("PPI", "ppi", List.of(alias("생산자물가", "생산자물가")));
        inactive.deactivate();
        economicTermRepository.saveAndFlush(inactive);
        entityManager.clear();

        List<EconomicTerm> result = economicTermRepository.findAllByStatusOrderByIdAsc(TermStatus.ACTIVE);

        assertThat(result).extracting(EconomicTerm::getId)
                .containsExactly(first.getId(), second.getId(), withoutAlias.getId());
        assertThat(result).allSatisfy(term -> assertThat(Hibernate.isInitialized(term.getAliases())).isTrue());
        assertThat(result.get(0).getAliases()).extracting(EconomicTermAlias::getNormalizedAlias)
                .containsExactly("국내총생산", "국민총생산지표");
        assertThat(result.get(2).getAliases()).isEmpty();
    }

    @Test
    void countsNewsMappings() {
        EconomicTerm term = economicTermRepository.saveAndFlush(term("GDP", "gdp", List.of()));
        NewsArticle article = newsArticleRepository.saveAndFlush(article());
        termNewsMappingRepository.saveAndFlush(new TermNewsMapping(
                term,
                article,
                MatchType.EXACT_NAME,
                BigDecimal.ONE,
                LocalDateTime.now()
        ));

        assertThat(termNewsMappingRepository.countByEconomicTermId(term.getId())).isEqualTo(1);
    }

    private PageRequest pageable() {
        return PageRequest.of(0, 20, Sort.by("name").ascending().and(Sort.by("id").ascending()));
    }

    private EconomicTerm term(String name, String normalizedName, List<EconomicTermAlias> aliases) {
        return new EconomicTerm(name, normalizedName, "정의", aliases);
    }

    private EconomicTermAlias alias(String alias, String normalizedAlias) {
        return new EconomicTermAlias(alias, normalizedAlias);
    }

    private NewsArticle article() {
        return new NewsArticle(
                "뉴스",
                "요약",
                "Example",
                "https://example.com/news/1",
                new byte[32],
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
