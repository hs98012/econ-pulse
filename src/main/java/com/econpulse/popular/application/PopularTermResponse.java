package com.econpulse.popular.application;

public record PopularTermResponse(
        int rank,
        long economicTermId,
        String name,
        String description,
        long score
) {

    public PopularTermResponse {
        if (rank < 1) {
            throw new IllegalArgumentException("Popular term rank must be positive.");
        }
        if (economicTermId <= 0) {
            throw new IllegalArgumentException("Economic term ID must be positive.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Economic term name must not be blank.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Economic term description must not be blank.");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Popular term score must not be negative.");
        }
    }
}
