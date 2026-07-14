package com.econpulse.news.api.dto;

import static com.econpulse.global.time.UtcTimeConverter.toInstant;

import com.econpulse.news.domain.NewsArticle;
import java.time.Instant;

public record NewsDetailResponse(
        Long id,
        String title,
        String summary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt,
        Instant collectedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static NewsDetailResponse from(NewsArticle article) {
        return new NewsDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSourceName(),
                article.getSourceUrl(),
                toInstant(article.getPublishedAt()),
                toInstant(article.getCollectedAt()),
                toInstant(article.getCreatedAt()),
                toInstant(article.getUpdatedAt())
        );
    }
}
