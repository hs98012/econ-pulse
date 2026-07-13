package com.econpulse.term.api.dto;

import com.econpulse.term.domain.EconomicTerm;
import java.util.List;

public record TermSummaryResponse(
        Long id,
        String name,
        String definition,
        List<String> aliases
) {

    public static TermSummaryResponse from(EconomicTerm economicTerm) {
        return new TermSummaryResponse(
                economicTerm.getId(),
                economicTerm.getName(),
                economicTerm.getDefinition(),
                economicTerm.getAliasValues()
        );
    }
}
