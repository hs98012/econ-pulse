package com.econpulse.mapping.domain;

import com.econpulse.global.domain.TextNormalizer;
import java.util.Comparator;
import java.util.List;

public record TermMatchTarget(
        Long termId,
        String name,
        List<String> aliases
) {

    public TermMatchTarget {
        requirePositive(termId, "termId");
        String normalizedName = normalizeRequired(name, "name");
        if (aliases == null) {
            throw new IllegalArgumentException("aliases must not be null");
        }

        name = normalizedName;
        aliases = aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(TextNormalizer::normalize)
                .filter(alias -> !alias.isBlank())
                .filter(alias -> !alias.equals(normalizedName))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return TextNormalizer.normalize(value);
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
