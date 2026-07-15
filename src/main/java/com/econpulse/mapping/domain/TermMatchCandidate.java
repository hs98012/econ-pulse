package com.econpulse.mapping.domain;

import java.math.BigDecimal;

public record TermMatchCandidate(
        Long economicTermId,
        Long newsArticleId,
        MatchType matchType,
        BigDecimal confidenceScore,
        String matchedText,
        MatchedField matchedField
) {
}
