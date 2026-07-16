package com.econpulse.popular.application;

import java.time.LocalDate;
import java.util.Objects;

public record PopularTermQuery(LocalDate date, int limit) {

    public static final int MAX_LIMIT = 100;

    public PopularTermQuery {
        Objects.requireNonNull(date, "Popular term date must not be null.");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Popular term limit must be between 1 and 100.");
        }
    }
}
