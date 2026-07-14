package com.econpulse.news.infrastructure.provider.naver;

import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.infrastructure.provider.ExternalNewsTextSanitizer;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

final class NaverNewsResponseMapper {

    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    NewsSearchResult map(NaverNewsSearchResponse response, NewsSearchQuery query, int expectedStart) {
        if (response == null || response.items() == null || response.total() == null
                || response.start() == null || response.display() == null
                || response.start() != expectedStart || response.display() != query.size()
                || response.total() < 0) {
            throw invalidResponse();
        }

        List<NewsProviderArticle> articles = response.items().stream().map(this::mapArticle).toList();
        long nextStart = (long) response.start() + response.display();
        boolean hasNext = !articles.isEmpty()
                && articles.size() == response.display()
                && nextStart <= response.total()
                && nextStart <= NaverNewsProvider.MAX_START;
        return new NewsSearchResult(
                articles,
                query.page(),
                query.size(),
                OptionalLong.of(response.total()),
                hasNext
        );
    }

    private NewsProviderArticle mapArticle(NaverNewsItemResponse item) {
        if (item == null) {
            throw invalidResponse();
        }
        String title = ExternalNewsTextSanitizer.sanitize(item.title());
        String summary = ExternalNewsTextSanitizer.sanitize(item.description());
        String sourceUrl = selectSourceUrl(item.originallink(), item.link());
        try {
            Instant publishedAt = ZonedDateTime.parse(requireText(item.pubDate()), NAVER_DATE_FORMAT).toInstant();
            return new NewsProviderArticle(
                    sourceUrl,
                    requireText(title),
                    summary,
                    sourceHost(sourceUrl),
                    sourceUrl,
                    publishedAt
            );
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private String selectSourceUrl(String originalLink, String link) {
        if (isValidUrl(originalLink)) {
            return originalLink.trim();
        }
        if (isValidUrl(link)) {
            return link.trim();
        }
        throw invalidResponse();
    }

    private boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sourceHost(String sourceUrl) {
        return requireText(URI.create(sourceUrl).getHost());
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private NewsProviderException invalidResponse() {
        return new NewsProviderException(
                NewsProviderErrorType.INVALID_RESPONSE,
                "News provider returned an invalid response."
        );
    }
}
