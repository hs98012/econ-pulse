package com.econpulse.news.application.port;

import java.util.List;
import java.util.OptionalLong;

public record NewsSearchResult(
        List<NewsProviderArticle> articles,
        int page,
        int size,
        OptionalLong totalElements,
        boolean hasNext
) {

    public NewsSearchResult {
        articles = List.copyOf(articles);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        totalElements = totalElements == null ? OptionalLong.empty() : totalElements;
    }
}
