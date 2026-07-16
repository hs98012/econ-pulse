package com.econpulse.mapping.api.internal;

import com.econpulse.mapping.application.AutoMapNewsResult;

public record AutoTermNewsMappingResponse(
        Long newsArticleId,
        long evaluatedTerms,
        long matchedCandidates,
        long created,
        long updated,
        long skipped,
        long noMatch
) {

    public static AutoTermNewsMappingResponse from(AutoMapNewsResult result) {
        return new AutoTermNewsMappingResponse(
                result.newsArticleId(),
                result.evaluatedTerms(),
                result.matchedCandidates(),
                result.created(),
                result.updated(),
                result.skipped(),
                result.noMatch()
        );
    }
}
