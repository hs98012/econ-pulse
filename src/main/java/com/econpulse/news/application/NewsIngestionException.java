package com.econpulse.news.application;

public class NewsIngestionException extends RuntimeException {

    public NewsIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
