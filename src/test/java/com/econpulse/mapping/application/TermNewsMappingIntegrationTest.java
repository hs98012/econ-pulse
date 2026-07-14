package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TermNewsMappingIntegrationTest extends AbstractIntegrationTest {

    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMappingRepository mappingRepository;

    @Autowired
    TermNewsMappingIntegrationTest(
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            NewsArticleRepository articleRepository,
            TermNewsMappingRepository mappingRepository
    ) {
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
    void sequentialCommandsAreIdempotentAndOnlyStrongerEvidenceUpdates() {
        EconomicTerm term = termRepository.saveAndFlush(term("GDP"));
        NewsArticle article = articleRepository.saveAndFlush(article());
        Instant createdAt = Instant.parse("2026-07-15T01:00:00Z");
        TermNewsMappingService createService = service(createdAt);

        TermNewsMappingResult created = createService.save(command(term, article, MatchType.ALIAS, "0.8000"));
        TermNewsMappingResult skipped = service(Instant.parse("2026-07-15T02:00:00Z"))
                .save(command(term, article, MatchType.ALIAS, "0.8"));

        assertThat(created.status()).isEqualTo(MappingSaveStatus.CREATED);
        assertThat(skipped.status()).isEqualTo(MappingSaveStatus.SKIPPED);
        assertThat(skipped.matchedAt()).isEqualTo(createdAt);
        assertThat(mappingRepository.count()).isOne();

        Instant updatedAt = Instant.parse("2026-07-15T03:00:00Z");
        TermNewsMappingResult updated = service(updatedAt)
                .save(command(term, article, MatchType.EXACT_NAME, "0.6000"));
        TermNewsMappingResult weaker = service(Instant.parse("2026-07-15T04:00:00Z"))
                .save(command(term, article, MatchType.ALIAS, "1.0000"));

        assertThat(updated.status()).isEqualTo(MappingSaveStatus.UPDATED);
        assertThat(updated.matchedAt()).isEqualTo(updatedAt);
        assertThat(weaker.status()).isEqualTo(MappingSaveStatus.SKIPPED);
        assertThat(mappingRepository.count()).isOne();
        TermNewsMapping stored = mappingRepository.findAll().get(0);
        assertThat(stored.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(stored.getConfidenceScore()).isEqualByComparingTo("0.6000");
        assertThat(stored.getMatchedAt()).isEqualTo("2026-07-15T03:00:00");
    }

    @Test
    void inactiveTermIsRejectedWithoutMappingRow() {
        EconomicTerm term = term("CPI");
        term.deactivate();
        termRepository.saveAndFlush(term);
        NewsArticle article = articleRepository.saveAndFlush(article());

        assertThatThrownBy(() -> service(Instant.parse("2026-07-15T01:00:00Z"))
                .save(command(term, article, MatchType.EXACT_NAME, "1.0000")))
                .isInstanceOf(InactiveTermException.class);
        assertThat(mappingRepository.count()).isZero();
    }

    private TermNewsMappingService service(Instant instant) {
        return new TermNewsMappingService(
                termRepository,
                articleRepository,
                mappingRepository,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private TermNewsMappingCommand command(
            EconomicTerm term,
            NewsArticle article,
            MatchType type,
            String score
    ) {
        return new TermNewsMappingCommand(term.getId(), article.getId(), type, new BigDecimal(score));
    }

    private EconomicTerm term(String name) {
        return new EconomicTerm(name, name.toLowerCase(), "definition", List.of());
    }

    private NewsArticle article() {
        var newsUrl = new NewsUrlHasher().hash("https://example.com/mapping/integration/1");
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
