package com.econpulse.mapping.application;

import static com.econpulse.global.time.UtcTimeConverter.toInstant;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.news.domain.NewsArticle;
import java.math.BigDecimal;
import java.time.Instant;

public record TermRelatedNewsResponse(
        Long id,
        String title,
        String summary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt,
        MatchType matchType,
        BigDecimal confidenceScore,
        Instant matchedAt
) {

    public static TermRelatedNewsResponse from(TermNewsMapping mapping) {
        NewsArticle article = mapping.getNewsArticle();
        return new TermRelatedNewsResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSourceName(),
                article.getSourceUrl(),
                toInstant(article.getPublishedAt()),
                mapping.getMatchType(),
                mapping.getConfidenceScore(),
                toInstant(mapping.getMatchedAt())
        );
    }
}
