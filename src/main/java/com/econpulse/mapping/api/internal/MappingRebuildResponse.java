package com.econpulse.mapping.api.internal;

import com.econpulse.mapping.application.TermNewsAutoMappingResult;

public record MappingRebuildResponse(
        int requestedNewsCount,
        int processedNewsCount,
        int activeTermCount,
        long evaluatedPairCount,
        long matchedCandidateCount,
        long created,
        long updated,
        long skipped,
        long unmatchedPairCount
) {

    public static MappingRebuildResponse from(TermNewsAutoMappingResult result) {
        return new MappingRebuildResponse(
                result.requestedNewsCount(),
                result.processedNewsCount(),
                result.activeTermCount(),
                result.evaluatedPairCount(),
                result.matchedCandidateCount(),
                result.created(),
                result.updated(),
                result.skipped(),
                result.unmatchedPairCount()
        );
    }
}
