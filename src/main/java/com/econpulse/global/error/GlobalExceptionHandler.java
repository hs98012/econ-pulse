package com.econpulse.global.error;

import jakarta.validation.ConstraintViolationException;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
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
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception
    ) {
        ErrorCode errorCode = resolveDuplicateCode(exception);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ErrorCode resolveDuplicateCode(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("economic_term_aliases")) {
            return ErrorCode.DUPLICATE_TERM_ALIAS;
        }
        return ErrorCode.DUPLICATE_TERM_NAME;
    }
}
