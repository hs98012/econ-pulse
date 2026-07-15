package com.econpulse.news.application.port;

import com.econpulse.global.domain.TextNormalizer;

public final class NewsTextNormalizer {

    private NewsTextNormalizer() {
    }

    public static String normalize(String value) {
        return TextNormalizer.normalize(value);
    }
}
