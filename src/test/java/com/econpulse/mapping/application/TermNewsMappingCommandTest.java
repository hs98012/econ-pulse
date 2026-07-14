package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.mapping.domain.MatchType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TermNewsMappingCommandTest {

    @Test
    void rejectsInvalidIds() {
        assertInvalid(null, 1L, MatchType.EXACT_NAME, "0.5000");
        assertInvalid(0L, 1L, MatchType.EXACT_NAME, "0.5000");
        assertInvalid(-1L, 1L, MatchType.EXACT_NAME, "0.5000");
        assertInvalid(1L, null, MatchType.EXACT_NAME, "0.5000");
        assertInvalid(1L, 0L, MatchType.EXACT_NAME, "0.5000");
        assertInvalid(1L, -1L, MatchType.EXACT_NAME, "0.5000");
    }

    @Test
    void rejectsNullTypeAndScore() {
        assertInvalid(1L, 1L, null, "0.5000");
        assertThatThrownBy(() -> new TermNewsMappingCommand(1L, 1L, MatchType.EXACT_NAME, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeOrOverScaleScores() {
        assertInvalid(1L, 1L, MatchType.EXACT_NAME, "-0.0001");
        assertInvalid(1L, 1L, MatchType.EXACT_NAME, "1.0001");
        assertInvalid(1L, 1L, MatchType.EXACT_NAME, "0.12345");
    }

    @Test
    void normalizesValidScoresToScaleFour() {
        assertThat(command("1").confidenceScore()).isEqualByComparingTo("1.0000").hasScaleOf(4);
        assertThat(command("0.8").confidenceScore()).isEqualByComparingTo("0.8000").hasScaleOf(4);
        assertThat(command("0.7500").confidenceScore()).isEqualByComparingTo("0.7500").hasScaleOf(4);
        assertThat(command("0.0000").confidenceScore()).isEqualByComparingTo("0.0000").hasScaleOf(4);
    }

    private TermNewsMappingCommand command(String score) {
        return new TermNewsMappingCommand(1L, 2L, MatchType.EXACT_NAME, new BigDecimal(score));
    }

    private void assertInvalid(Long termId, Long articleId, MatchType type, String score) {
        assertThatThrownBy(() -> new TermNewsMappingCommand(
                termId,
                articleId,
                type,
                new BigDecimal(score)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
