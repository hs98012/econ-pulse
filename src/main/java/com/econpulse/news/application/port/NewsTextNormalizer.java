package com.econpulse.news.application.port;

import java.text.Normalizer;
import java.util.Locale;

public final class NewsTextNormalizer {

    private NewsTextNormalizer() {
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
