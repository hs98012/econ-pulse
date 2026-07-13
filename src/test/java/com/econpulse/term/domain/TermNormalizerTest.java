package com.econpulse.term.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TermNormalizerTest {

    @Test
    void normalizesWithNfkcWhitespaceAndLowercase() {
        assertThat(TermNormalizer.normalize(" ＧＤＰ　 성장  "))
                .isEqualTo("gdp 성장");
    }
}
