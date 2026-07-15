package com.econpulse.mapping.api.internal;

import com.econpulse.mapping.application.TermNewsAutoMappingCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record MappingRebuildRequest(
        @NotEmpty
        @Size(max = TermNewsAutoMappingCommand.MAX_NEWS_COUNT)
        List<@NotNull @Positive Long> newsArticleIds
) {

    public MappingRebuildRequest {
        newsArticleIds = newsArticleIds == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(newsArticleIds));
    }

    public TermNewsAutoMappingCommand toCommand() {
        return new TermNewsAutoMappingCommand(newsArticleIds);
    }
}
