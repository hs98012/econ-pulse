package com.econpulse.mapping.application;

import java.util.Comparator;
import java.util.List;

public record TermNewsAutoMappingCommand(List<Long> newsArticleIds) {

    public static final int MAX_NEWS_COUNT = 100;

    public TermNewsAutoMappingCommand {
        if (newsArticleIds == null) {
            throw new IllegalArgumentException("newsArticleIds must not be null");
        }
        if (newsArticleIds.isEmpty()) {
            throw new IllegalArgumentException("newsArticleIds must not be empty");
        }
        if (newsArticleIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("newsArticleIds must contain only positive values");
        }

        newsArticleIds = newsArticleIds.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (newsArticleIds.size() > MAX_NEWS_COUNT) {
            throw new IllegalArgumentException("newsArticleIds must contain at most 100 unique values");
        }
    }

    public static TermNewsAutoMappingCommand single(Long newsArticleId) {
        return new TermNewsAutoMappingCommand(List.of(newsArticleId));
    }
}
