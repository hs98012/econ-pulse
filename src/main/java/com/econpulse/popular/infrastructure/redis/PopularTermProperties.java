package com.econpulse.popular.infrastructure.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "econpulse.popular-terms")
public record PopularTermProperties(String keyPrefix, Duration retention, int maxQuerySize) {

    public PopularTermProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("Popular term key prefix must not be blank.");
        }
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalStateException("Popular term retention must be positive.");
        }
        if (maxQuerySize < 1 || maxQuerySize > 100) {
            throw new IllegalStateException("Popular term max query size must be between 1 and 100.");
        }
    }
}
