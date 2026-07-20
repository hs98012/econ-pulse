package com.econpulse.term.application;

import com.econpulse.global.time.UtcTimeConverter;
import com.econpulse.term.domain.EconomicTerm;
import java.time.Instant;
import java.util.List;

public record EconomicTermDetailResult(
        Long id,
        String name,
        String definition,
        List<String> aliases,
        long latestNewsCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static EconomicTermDetailResult from(EconomicTerm economicTerm, long latestNewsCount) {
        return new EconomicTermDetailResult(
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
