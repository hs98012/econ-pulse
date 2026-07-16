package com.econpulse.popular.application;

public record PopularTermScore(long economicTermId, long score, int rank) {

    public PopularTermScore {
        if (economicTermId <= 0) {
            throw new IllegalArgumentException("Economic term ID must be positive.");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Popular term score must not be negative.");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("Popular term rank must be positive.");
        }
    }
}
