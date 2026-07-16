package com.econpulse.mapping.application;

public record AutoMapNewsResult(
        Long newsArticleId,
        long evaluatedTerms,
        long matchedCandidates,
        long created,
        long updated,
        long skipped,
        long noMatch
) {

    public AutoMapNewsResult {
        if (newsArticleId == null || newsArticleId <= 0) {
            throw new IllegalArgumentException("newsArticleId must be positive");
        }
        if (evaluatedTerms < 0 || matchedCandidates < 0 || created < 0
                || updated < 0 || skipped < 0 || noMatch < 0) {
            throw new IllegalArgumentException("auto mapping result counts must not be negative");
        }
        if (evaluatedTerms != matchedCandidates + noMatch) {
            throw new IllegalArgumentException("evaluated terms must equal matched candidates plus no match");
        }
        if (matchedCandidates != created + updated + skipped) {
            throw new IllegalArgumentException("matched candidates must equal mapping save results");
        }
    }
}
