package com.econpulse.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "Economic term was not found."),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "News article was not found."),
    DUPLICATE_TERM_NAME(HttpStatus.CONFLICT, "Economic term name already exists."),
    DUPLICATE_TERM_ALIAS(HttpStatus.CONFLICT, "Economic term alias already exists."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
