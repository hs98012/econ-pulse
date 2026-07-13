package com.econpulse.news.application;

public record NewsIngestionResult(
        int fetched,
        int created,
        int updated,
        int skipped
) {
}
