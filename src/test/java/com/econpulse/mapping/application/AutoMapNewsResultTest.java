package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class AutoMapNewsResultTest {

    @Test
    void rejectsNegativeCountsAndBrokenInvariants() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AutoMapNewsResult(1L, -1, 0, 0, 0, 0, 0)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AutoMapNewsResult(1L, 2, 1, 1, 0, 0, 0)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AutoMapNewsResult(1L, 1, 1, 0, 0, 0, 0)
        );
    }
}
