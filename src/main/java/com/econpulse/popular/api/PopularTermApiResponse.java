package com.econpulse.popular.api;

import com.econpulse.popular.application.PopularTermResponse;

public record PopularTermApiResponse(
        int rank,
        long economicTermId,
        String name,
        String description,
        long score
) {

    public static PopularTermApiResponse from(PopularTermResponse response) {
        return new PopularTermApiResponse(
                response.rank(),
                response.economicTermId(),
                response.name(),
                response.description(),
                response.score()
        );
    }
}
