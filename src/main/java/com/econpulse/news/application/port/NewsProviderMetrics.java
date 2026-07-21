package com.econpulse.news.application.port;

public interface NewsProviderMetrics {

    NewsProviderMetrics NO_OP = () -> new Request() {
        @Override
        public void success() {
        }

        @Override
        public void failure(NewsProviderErrorType errorType) {
        }
    };

    Request startRequest();

    interface Request {

        void success();

        void failure(NewsProviderErrorType errorType);
    }
}
