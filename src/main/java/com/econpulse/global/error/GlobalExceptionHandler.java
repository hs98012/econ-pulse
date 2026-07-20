package com.econpulse.global.error;

import com.econpulse.popular.application.port.PopularTermStoreException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PopularTermStoreException.class)
    public ResponseEntity<ErrorResponse> handlePopularTermStoreException(
            PopularTermStoreException exception,
            HttpServletRequest request
    ) {
        if (exception.getReason() == PopularTermStoreException.Reason.UNAVAILABLE) {
            ErrorCode errorCode = ErrorCode.POPULAR_TERM_STORE_UNAVAILABLE;
            logExpected(errorCode, request);
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ErrorResponse.of(errorCode));
        }
        logUnexpected(exception, request);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        logExpected(errorCode, request);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        logExpected(ErrorCode.INVALID_REQUEST, request);
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.INVALID_REQUEST, request);
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        logExpected(ErrorCode.INVALID_REQUEST, request);
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = resolveDuplicateCode(exception);
        logExpected(errorCode, request);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        LOGGER.atWarn()
                .addKeyValue("event", "http_request_error")
                .addKeyValue("errorCode", "RESOURCE_NOT_FOUND")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("http_request_error");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        logUnexpected(exception, request);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private void logExpected(ErrorCode errorCode, HttpServletRequest request) {
        LOGGER.atWarn()
                .addKeyValue("event", "http_request_error")
                .addKeyValue("errorCode", errorCode.name())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("http_request_error");
    }

    private void logUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.atError()
                .addKeyValue("event", "unexpected_http_error")
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.name())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .setCause(exception)
                .log("unexpected_http_error");
    }

    private ErrorCode resolveDuplicateCode(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("economic_term_aliases")) {
            return ErrorCode.DUPLICATE_TERM_ALIAS;
        }
        return ErrorCode.DUPLICATE_TERM_NAME;
    }
}
