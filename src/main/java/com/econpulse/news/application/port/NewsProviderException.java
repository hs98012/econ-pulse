package com.econpulse.news.application.port;

import java.util.Objects;

public class NewsProviderException extends RuntimeException {

    private final NewsProviderErrorType errorType;

    public NewsProviderException(NewsProviderErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public NewsProviderException(NewsProviderErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public NewsProviderErrorType getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return errorType.isRetryable();
    }
}
