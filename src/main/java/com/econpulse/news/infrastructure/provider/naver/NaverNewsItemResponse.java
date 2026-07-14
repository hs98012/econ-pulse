package com.econpulse.news.infrastructure.provider.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record NaverNewsItemResponse(
        String title,
        String originallink,
        String link,
        String description,
        String pubDate
) {
}
