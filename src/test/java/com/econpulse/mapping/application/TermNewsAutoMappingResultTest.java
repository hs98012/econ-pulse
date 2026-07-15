package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TermNewsAutoMappingResultTest {

    @Test
    void rejectsBrokenCountRelationships() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TermNewsAutoMappingResult(1, 1, 1, 2, 1, 1, 0, 0, 0)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TermNewsAutoMappingResult(1, 1, 1, 1, 1, 0, 0, 0, 0)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TermNewsAutoMappingResult(-1, 0, 0, 0, 0, 0, 0, 0, 0)
        );
    }
}
