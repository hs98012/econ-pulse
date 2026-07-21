package com.econpulse.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.mapping.application.TermRelatedNewsQuery;
import com.econpulse.mapping.application.TermRelatedNewsQueryService;
import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.application.EconomicTermService;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountIntegrationTest extends AbstractIntegrationTest {

    private final EconomicTermService termService;
    private final TermRelatedNewsQueryService relatedNewsQueryService;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;
    private final Statistics statistics;

    @Autowired
    QueryCountIntegrationTest(
            EconomicTermService termService,
            TermRelatedNewsQueryService relatedNewsQueryService,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository,
            EntityManagerFactory entityManagerFactory
    ) {
        this.termService = termService;
        this.relatedNewsQueryService = relatedNewsQueryService;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.articleRepository = articleRepository;
        this.mappingRepository = mappingRepository;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        mappingRepository.deleteAll();
        articleRepository.deleteAll();
        aliasRepository.deleteAll();
        termRepository.deleteAll();
        for (int index = 1; index <= 5; index++) {
            termService.create(new TermCreateRequest(
                    "용어-" + index,
                    "정의-" + index,
                    List.of("별칭-" + index)
            ));
        }
        statistics.clear();
    }

    @Test
    void termListAndSearchUseConstantPageCountAndAliasHydrationQueries() {
        assertThat(termService.find(null, 0, 3).content()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

        statistics.clear();
        assertThat(termService.find("용어", 0, 3).content()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void activeTermAliasGraphAndPopularIdLookupEachUseOneQuery() {
        List<EconomicTerm> active = termRepository.findAllByStatusOrderByIdAsc(TermStatus.ACTIVE);
        assertThat(active).hasSize(5).allMatch(term -> term.getAliases().size() == 1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        statistics.clear();
        List<Long> ids = active.stream().limit(3).map(EconomicTerm::getId).toList();
        assertThat(termRepository.findAllByIdInAndStatus(ids, TermStatus.ACTIVE)).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void relatedNewsPageFetchesArticlesWithoutPerRowQueries() {
        EconomicTerm term = termRepository.findAll().get(0);
        for (int index = 1; index <= 3; index++) {
            NewsArticle article = articleRepository.saveAndFlush(article(index));
            mappingRepository.saveAndFlush(new TermNewsMapping(
                    term,
                    article,
                    MatchType.EXACT_NAME,
                    new BigDecimal("1.0000"),
                    LocalDateTime.parse("2026-07-21T00:00:00")
            ));
        }
        statistics.clear();

        assertThat(relatedNewsQueryService.find(new TermRelatedNewsQuery(term.getId(), 0, 2)).content())
                .hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    private NewsArticle article(int sequence) {
        var url = new NewsUrlHasher().hash("https://example.com/query-count/" + sequence);
        return NewsArticle.create(
                "뉴스-" + sequence,
                "요약",
                "source",
                url.normalizedUrl(),
                url.hash(),
                Instant.parse("2026-07-21T00:00:00Z").minusSeconds(sequence),
                Instant.parse("2026-07-21T01:00:00Z")
        );
    }
}
