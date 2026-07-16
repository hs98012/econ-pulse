package com.econpulse.popular.application.port;

public class PopularTermStoreException extends RuntimeException {

    private final Reason reason;

    public PopularTermStoreException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PopularTermStoreException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        UNAVAILABLE,
        INVALID_DATA
    }
}
