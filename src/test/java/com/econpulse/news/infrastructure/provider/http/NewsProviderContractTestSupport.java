package com.econpulse.news.infrastructure.provider.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class NewsProviderContractTestSupport {

    private NewsProviderContractTestSupport() {
    }

    static String fixture(String name) {
        String path = "news-provider/" + name;
        try (InputStream input = NewsProviderContractTestSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture: " + path, exception);
        }
    }
}
