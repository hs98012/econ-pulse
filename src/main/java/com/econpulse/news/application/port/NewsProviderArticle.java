package com.econpulse.news.application.port;

import java.time.Instant;
import java.util.Objects;

public record NewsProviderArticle(
        String providerArticleId,
        String title,
        String summary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt
) {

    public NewsProviderArticle {
        providerArticleId = requireText(providerArticleId, "providerArticleId");
        title = requireText(title, "title");
        summary = summary == null ? "" : summary;
        sourceName = requireText(sourceName, "sourceName");
        sourceUrl = requireText(sourceUrl, "sourceUrl");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
