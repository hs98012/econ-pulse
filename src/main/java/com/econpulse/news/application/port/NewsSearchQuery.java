package com.econpulse.news.application.port;

import java.util.Objects;

public record NewsSearchQuery(
        String query,
        int page,
        int size,
        NewsSort sort
) {

    public static final int MAX_SIZE = 100;

    public NewsSearchQuery {
        query = NewsTextNormalizer.normalize(Objects.requireNonNull(query, "query must not be null"));
        sort = Objects.requireNonNull(sort, "sort must not be null");

        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
