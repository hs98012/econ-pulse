package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class AutoMapNewsCommandTest {

    @Test
    void acceptsPositiveNewsArticleId() {
        assertThat(new AutoMapNewsCommand(1L).newsArticleId()).isEqualTo(1L);
    }

    @Test
    void rejectsNullOrNonPositiveNewsArticleId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AutoMapNewsCommand(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AutoMapNewsCommand(0L));
        assertThatIllegalArgumentException().isThrownBy(() -> new AutoMapNewsCommand(-1L));
    }
}
