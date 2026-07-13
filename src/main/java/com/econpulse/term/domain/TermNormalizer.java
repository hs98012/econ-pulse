package com.econpulse.term.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class TermNormalizer {

    private TermNormalizer() {
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
