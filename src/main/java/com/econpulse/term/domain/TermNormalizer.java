package com.econpulse.term.domain;

import com.econpulse.global.domain.TextNormalizer;

public final class TermNormalizer {

    private TermNormalizer() {
    }

    public static String normalize(String value) {
        return TextNormalizer.normalize(value);
    }
}
