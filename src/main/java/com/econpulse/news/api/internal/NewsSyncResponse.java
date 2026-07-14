package com.econpulse.news.api.internal;

import com.econpulse.news.application.NewsIngestionResult;

public record NewsSyncResponse(
        int fetched,
        int created,
        int updated,
        int skipped
) {

    public static NewsSyncResponse from(NewsIngestionResult result) {
        return new NewsSyncResponse(
                result.fetched(),
                result.created(),
                result.updated(),
                result.skipped()
        );
    }
}
