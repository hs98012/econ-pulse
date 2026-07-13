package com.econpulse.news.application;

import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSort;

public record NewsIngestionCommand(
        String query,
        int page,
        int size,
        NewsSort sort
) {

    public NewsIngestionCommand {
        new NewsSearchQuery(query, page, size, sort);
    }

    NewsSearchQuery toSearchQuery() {
        return new NewsSearchQuery(query, page, size, sort);
    }
}
