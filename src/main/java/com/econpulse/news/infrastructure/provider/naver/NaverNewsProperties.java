package com.econpulse.news.infrastructure.provider.naver;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "econpulse.news.naver")
public record NaverNewsProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout
) {

    public NaverNewsProperties {
        requireText(baseUrl, "Naver news base URL must be configured.");
        requireText(clientId, "Naver news client ID must be configured.");
        requireText(clientSecret, "Naver news client secret must be configured.");
        requirePositive(connectTimeout, "Naver news connect timeout must be positive.");
        requirePositive(readTimeout, "Naver news read timeout must be positive.");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private static void requirePositive(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(message);
        }
    }
}
