package com.econpulse.mapping.application;

public record AutoMapNewsCommand(Long newsArticleId) {

    public AutoMapNewsCommand {
        if (newsArticleId == null || newsArticleId <= 0) {
            throw new IllegalArgumentException("newsArticleId must be positive");
        }
    }
}
