package com.econpulse.mapping.domain;

public enum MatchType {
    EXACT_NAME(2),
    ALIAS(1);

    private final int priority;

    MatchType(int priority) {
        this.priority = priority;
    }

    public boolean hasHigherPriorityThan(MatchType other) {
        return other != null && priority > other.priority;
    }
}
