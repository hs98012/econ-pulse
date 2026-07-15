package com.econpulse.mapping.domain;

import com.econpulse.global.domain.TextNormalizer;

public record NewsMatchContent(
        Long newsArticleId,
        String title,
        String summary
) {

    public NewsMatchContent {
        if (newsArticleId == null || newsArticleId <= 0) {
            throw new IllegalArgumentException("newsArticleId must be positive");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        title = TextNormalizer.normalize(title);
        summary = summary == null || summary.isBlank() ? "" : TextNormalizer.normalize(summary);
    }
}
