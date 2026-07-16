package com.econpulse.popular.application;

public record RecordTermSearchCommand(Long economicTermId) {

    public RecordTermSearchCommand {
        if (economicTermId == null || economicTermId <= 0) {
            throw new IllegalArgumentException("Economic term ID must be positive.");
        }
    }
}
