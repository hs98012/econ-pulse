package com.econpulse.news.application;

import java.util.Arrays;

public record NewsUrl(
        String normalizedUrl,
        byte[] hash
) {

    public NewsUrl {
        hash = hash.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    String hashKey() {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NewsUrl newsUrl)) {
            return false;
        }
        return normalizedUrl.equals(newsUrl.normalizedUrl) && Arrays.equals(hash, newsUrl.hash);
    }

    @Override
    public int hashCode() {
        int result = normalizedUrl.hashCode();
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}
