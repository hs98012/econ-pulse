package com.econpulse.news.api.internal;

import com.econpulse.global.error.ErrorCode;
import com.econpulse.global.error.ErrorResponse;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NewsSyncController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NewsProviderExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsProviderExceptionHandler.class);

    @ExceptionHandler(NewsProviderException.class)
    public ResponseEntity<ErrorResponse> handleNewsProviderException(
            NewsProviderException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = toErrorCode(exception.getErrorType());
        LOGGER.atWarn()
                .addKeyValue("event", "news_provider_request_failed")
                .addKeyValue("provider", "news")
                .addKeyValue("errorType", exception.getErrorType().name())
                .addKeyValue("retryable", exception.isRetryable())
                .addKeyValue("errorCode", errorCode.name())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("news_provider_request_failed");
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    private ErrorCode toErrorCode(NewsProviderErrorType errorType) {
        if (errorType == NewsProviderErrorType.INVALID_REQUEST) {
            return ErrorCode.INVALID_REQUEST;
        }
        if (errorType == NewsProviderErrorType.INVALID_RESPONSE
                || errorType == NewsProviderErrorType.AUTHENTICATION_FAILED) {
            return ErrorCode.NEWS_PROVIDER_BAD_RESPONSE;
        }
        return ErrorCode.NEWS_PROVIDER_UNAVAILABLE;
    }
}
