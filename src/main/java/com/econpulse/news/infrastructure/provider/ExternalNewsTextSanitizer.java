package com.econpulse.news.infrastructure.provider;

public final class ExternalNewsTextSanitizer {

    private ExternalNewsTextSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        return value.replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
