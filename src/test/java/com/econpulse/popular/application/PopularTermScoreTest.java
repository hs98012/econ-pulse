package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PopularTermScoreTest {

    @Test
    void acceptsPositiveIdNonNegativeScoreAndOneBasedRank() {
        PopularTermScore score = new PopularTermScore(3L, 0L, 1);

        assertThat(score.rank()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new PopularTermScore(0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermScore(1, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermScore(1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
