package com.econpulse.news.api.internal;

import com.econpulse.news.application.NewsIngestionCommand;
import com.econpulse.news.application.port.NewsSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NewsSyncRequest(
        @NotBlank String query,
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        @NotNull NewsSort sort
) {

    public NewsIngestionCommand toCommand() {
        return new NewsIngestionCommand(query, page, size, sort);
    }
}
