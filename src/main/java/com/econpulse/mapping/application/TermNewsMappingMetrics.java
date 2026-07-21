package com.econpulse.mapping.application;

public interface TermNewsMappingMetrics {

    TermNewsMappingMetrics NO_OP = () -> new Run() {
        @Override
        public void success(AutoMapNewsResult result) {
        }

        @Override
        public void failure() {
        }
    };

    Run start();

    interface Run {

        void success(AutoMapNewsResult result);

        void failure();
    }
}
