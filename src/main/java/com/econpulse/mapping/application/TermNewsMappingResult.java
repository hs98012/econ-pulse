package com.econpulse.mapping.application;

import com.econpulse.global.time.UtcTimeConverter;
import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import java.math.BigDecimal;
import java.time.Instant;

public record TermNewsMappingResult(
        Long mappingId,
        Long economicTermId,
        Long newsArticleId,
        MatchType matchType,
        BigDecimal confidenceScore,
        MappingSaveStatus status,
        Instant matchedAt
) {

    static TermNewsMappingResult from(TermNewsMapping mapping, MappingSaveStatus status) {
        return new TermNewsMappingResult(
                mapping.getId(),
                mapping.getEconomicTerm().getId(),
                mapping.getNewsArticle().getId(),
                mapping.getMatchType(),
                mapping.getConfidenceScore(),
                status,
                UtcTimeConverter.toInstant(mapping.getMatchedAt())
        );
    }
}
