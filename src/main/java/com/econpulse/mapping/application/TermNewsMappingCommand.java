package com.econpulse.mapping.application;

import com.econpulse.mapping.domain.MatchType;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record TermNewsMappingCommand(
        Long economicTermId,
        Long newsArticleId,
        MatchType matchType,
        BigDecimal confidenceScore
) {

    private static final BigDecimal MIN_SCORE = new BigDecimal("0.0000");
    private static final BigDecimal MAX_SCORE = new BigDecimal("1.0000");
    private static final int SCORE_SCALE = 4;

    public TermNewsMappingCommand {
        requirePositive(economicTermId, "economicTermId");
        requirePositive(newsArticleId, "newsArticleId");
        if (matchType == null) {
            throw new IllegalArgumentException("matchType must not be null");
        }
        if (confidenceScore == null) {
            throw new IllegalArgumentException("confidenceScore must not be null");
        }
        if (confidenceScore.scale() > SCORE_SCALE) {
            throw new IllegalArgumentException("confidenceScore must have at most 4 decimal places");
        }
        if (confidenceScore.compareTo(MIN_SCORE) < 0 || confidenceScore.compareTo(MAX_SCORE) > 0) {
            throw new IllegalArgumentException("confidenceScore must be between 0.0000 and 1.0000");
        }
        confidenceScore = confidenceScore.setScale(SCORE_SCALE, RoundingMode.UNNECESSARY);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
