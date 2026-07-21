package com.econpulse.news.application.port;

import com.econpulse.news.application.NewsIngestionResult;

public interface NewsIngestionMetrics {

    NewsIngestionMetrics NO_OP = () -> new Run() {
        @Override
        public void success(NewsIngestionResult result) {
        }

        @Override
        public void failure() {
        }
    };

    Run start();

    interface Run {

        void success(NewsIngestionResult result);

        void failure();
    }
}
