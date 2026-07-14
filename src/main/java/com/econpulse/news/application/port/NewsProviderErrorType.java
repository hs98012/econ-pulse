package com.econpulse.news.application.port;

public enum NewsProviderErrorType {
    INVALID_REQUEST(false),
    AUTHENTICATION_FAILED(false),
    RATE_LIMITED(true),
    TIMEOUT(true),
    INVALID_RESPONSE(false),
    TEMPORARY_FAILURE(true);

    private final boolean retryable;

    NewsProviderErrorType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
