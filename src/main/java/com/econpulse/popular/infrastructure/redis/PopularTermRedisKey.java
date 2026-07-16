package com.econpulse.popular.infrastructure.redis;

import java.time.LocalDate;
import java.util.Objects;

public final class PopularTermRedisKey {

    private final String prefix;

    public PopularTermRedisKey(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Popular term key prefix must not be blank.");
        }
        this.prefix = prefix;
    }

    public String daily(LocalDate date) {
        return prefix + ":" + Objects.requireNonNull(date, "Popular term date must not be null.");
    }
}
