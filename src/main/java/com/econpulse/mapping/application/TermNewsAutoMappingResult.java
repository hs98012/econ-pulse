package com.econpulse.mapping.application;

public record TermNewsAutoMappingResult(
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

    public TermNewsAutoMappingResult {
        if (requestedNewsCount < 0 || processedNewsCount < 0 || activeTermCount < 0
                || evaluatedPairCount < 0 || matchedCandidateCount < 0
                || created < 0 || updated < 0 || skipped < 0 || unmatchedPairCount < 0) {
            throw new IllegalArgumentException("mapping result counts must not be negative");
        }
        if (evaluatedPairCount != matchedCandidateCount + unmatchedPairCount) {
            throw new IllegalArgumentException("evaluated pairs must equal matched plus unmatched pairs");
        }
        if (matchedCandidateCount != created + updated + skipped) {
            throw new IllegalArgumentException("matched candidates must equal mapping save results");
        }
    }
}
