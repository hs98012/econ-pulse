package com.econpulse.news.infrastructure.provider.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record NaverNewsSearchResponse(
        String lastBuildDate,
        Integer total,
        Integer start,
        Integer display,
        List<NaverNewsItemResponse> items
) {
}
