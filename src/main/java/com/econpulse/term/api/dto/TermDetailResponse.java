package com.econpulse.term.api.dto;

import com.econpulse.term.domain.EconomicTerm;
import java.time.Instant;
import com.econpulse.global.time.UtcTimeConverter;
import java.util.List;

public record TermDetailResponse(
        Long id,
        String name,
        String definition,
        List<String> aliases,
        long latestNewsCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static TermDetailResponse from(EconomicTerm economicTerm, long latestNewsCount) {
        return new TermDetailResponse(
                economicTerm.getId(),
                economicTerm.getName(),
                economicTerm.getDefinition(),
                economicTerm.getAliasValues(),
                latestNewsCount,
                UtcTimeConverter.toInstant(economicTerm.getCreatedAt()),
                UtcTimeConverter.toInstant(economicTerm.getUpdatedAt())
        );
    }
}
