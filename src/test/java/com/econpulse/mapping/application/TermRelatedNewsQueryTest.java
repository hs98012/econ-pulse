package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TermRelatedNewsQueryTest {

    @Test
    void acceptsValidValuesAndExposesPagingDefaults() {
        assertThat(new TermRelatedNewsQuery(1L, 0, 100))
                .isEqualTo(new TermRelatedNewsQuery(1L, 0, 100));
        assertThat(TermRelatedNewsQuery.DEFAULT_PAGE).isZero();
        assertThat(TermRelatedNewsQuery.DEFAULT_SIZE).isEqualTo(20);
    }

    @Test
    void rejectsInvalidTermPageAndSize() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TermRelatedNewsQuery(0L, 0, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermRelatedNewsQuery(1L, -1, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermRelatedNewsQuery(1L, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermRelatedNewsQuery(1L, 0, 101));
    }
}
