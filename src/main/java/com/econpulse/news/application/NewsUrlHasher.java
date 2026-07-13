package com.econpulse.news.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NewsUrlHasher {

    private final NewsUrlNormalizer normalizer;

    public NewsUrlHasher() {
        this(new NewsUrlNormalizer());
    }

    public NewsUrlHasher(NewsUrlNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public NewsUrl hash(String sourceUrl) {
        String normalizedUrl = normalizer.normalize(sourceUrl);
        return new NewsUrl(normalizedUrl, sha256(normalizedUrl));
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
