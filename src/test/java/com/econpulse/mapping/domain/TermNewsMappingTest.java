package com.econpulse.mapping.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.domain.NewsArticle;
import com.econpulse.term.domain.EconomicTerm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermNewsMappingTest {

    private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.parse("2026-07-14T00:00:00");
    private static final LocalDateTime NEW_TIME = LocalDateTime.parse("2026-07-15T00:00:00");

    @Test
    void comparesSameEvidenceNumericallyAcrossBigDecimalScales() {
        TermNewsMapping mapping = mapping(MatchType.ALIAS, "0.8000");

        assertThat(mapping.hasSameEvidence(MatchType.ALIAS, new BigDecimal("0.8"))).isTrue();
        assertThat(mapping.hasSameEvidence(MatchType.EXACT_NAME, new BigDecimal("0.8"))).isFalse();
    }

    @Test
    void exactNameHasPriorityOverAliasRegardlessOfScore() {
        TermNewsMapping mapping = mapping(MatchType.ALIAS, "0.9000");

        assertThat(mapping.updateEvidenceIfStronger(
                MatchType.EXACT_NAME,
                new BigDecimal("0.5000"),
                NEW_TIME
        )).isTrue();
        assertThat(mapping.getMatchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(mapping.getConfidenceScore()).isEqualByComparingTo("0.5000");
        assertThat(mapping.getMatchedAt()).isEqualTo(NEW_TIME);
    }

    @Test
    void weakerTypeNeverReplacesExistingEvidence() {
        TermNewsMapping mapping = mapping(MatchType.EXACT_NAME, "0.5000");

        assertThat(mapping.updateEvidenceIfStronger(
                MatchType.ALIAS,
                new BigDecimal("1.0000"),
                NEW_TIME
        )).isFalse();
        assertUnchanged(mapping, MatchType.EXACT_NAME, "0.5000");
    }

    @Test
    void sameTypeUpdatesOnlyForNumericallyHigherScore() {
        TermNewsMapping mapping = mapping(MatchType.ALIAS, "0.7000");

        assertThat(mapping.updateEvidenceIfStronger(MatchType.ALIAS, new BigDecimal("0.700"), NEW_TIME))
                .isFalse();
        assertThat(mapping.updateEvidenceIfStronger(MatchType.ALIAS, new BigDecimal("0.6000"), NEW_TIME))
                .isFalse();
        assertUnchanged(mapping, MatchType.ALIAS, "0.7000");

        assertThat(mapping.updateEvidenceIfStronger(MatchType.ALIAS, new BigDecimal("0.8000"), NEW_TIME))
                .isTrue();
        assertThat(mapping.getConfidenceScore()).isEqualByComparingTo("0.8000");
        assertThat(mapping.getMatchedAt()).isEqualTo(NEW_TIME);
    }

    private TermNewsMapping mapping(MatchType type, String score) {
        return new TermNewsMapping(
                new EconomicTerm("GDP", "gdp", "definition", List.of()),
                new NewsArticle(
                        "title",
                        "summary",
                        "source",
                        "https://example.com/news/1",
                        new byte[32],
                        ORIGINAL_TIME,
                        ORIGINAL_TIME
                ),
                type,
                new BigDecimal(score),
                ORIGINAL_TIME
        );
    }

    private void assertUnchanged(TermNewsMapping mapping, MatchType type, String score) {
        assertThat(mapping.getMatchType()).isEqualTo(type);
        assertThat(mapping.getConfidenceScore()).isEqualByComparingTo(score);
        assertThat(mapping.getMatchedAt()).isEqualTo(ORIGINAL_TIME);
    }
}
