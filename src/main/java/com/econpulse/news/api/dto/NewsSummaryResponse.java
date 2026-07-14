package com.econpulse.news.api.dto;

import static com.econpulse.global.time.UtcTimeConverter.toInstant;

import com.econpulse.news.domain.NewsArticle;
import java.time.Instant;

public record NewsSummaryResponse(
        Long id,
        String title,
        String summary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt
) {

    public static NewsSummaryResponse from(NewsArticle article) {
        return new NewsSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSourceName(),
                article.getSourceUrl(),
                toInstant(article.getPublishedAt())
        );
    }
}
