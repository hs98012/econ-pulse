package com.econpulse.global.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class UtcTimeConverter {

    private UtcTimeConverter() {
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
