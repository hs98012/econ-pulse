package com.econpulse.term.api.dto;

import com.econpulse.term.domain.EconomicTerm;
import java.time.LocalDateTime;
import java.util.List;

public record TermResponse(
        Long id,
        String name,
        String definition,
        List<String> aliases,
        int latestNewsCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TermResponse from(EconomicTerm economicTerm) {
        return new TermResponse(
                economicTerm.getId(),
                economicTerm.getName(),
                economicTerm.getDefinition(),
                List.copyOf(economicTerm.getAliases()),
                economicTerm.getNewsMappings().size(),
                economicTerm.getCreatedAt(),
                economicTerm.getUpdatedAt()
        );
    }
}
