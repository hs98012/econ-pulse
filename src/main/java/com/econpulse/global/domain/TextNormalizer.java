package com.econpulse.global.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
