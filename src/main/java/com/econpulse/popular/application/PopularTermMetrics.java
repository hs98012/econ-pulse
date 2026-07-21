package com.econpulse.popular.application;

public interface PopularTermMetrics {

    PopularTermMetrics NO_OP = new PopularTermMetrics() {
        @Override
        public void recordSucceeded() {
        }

        @Override
        public void recordUnavailable() {
        }

        @Override
        public Query startQuery() {
            return new Query() {
                @Override
                public void success() {
                }

                @Override
                public void unavailable() {
                }

                @Override
                public void failure() {
                }
            };
        }
    };

    void recordSucceeded();

    void recordUnavailable();

    Query startQuery();

    interface Query {

        void success();

        void unavailable();

        void failure();
    }
}
