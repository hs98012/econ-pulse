package com.econpulse.popular.infrastructure.metrics;

import com.econpulse.popular.application.PopularTermMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MicrometerPopularTermMetrics implements PopularTermMetrics {

    static final String RECORD = "econpulse.popular_term.record";
    static final String QUERY = "econpulse.popular_term.query";
    static final String QUERY_DURATION = "econpulse.popular_term.query.duration";

    private final MeterRegistry registry;

    public MicrometerPopularTermMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordSucceeded() {
        incrementRecord("success");
    }

    @Override
    public void recordUnavailable() {
        incrementRecord("unavailable");
    }

    @Override
    public Query startQuery() {
        Timer.Sample sample = Timer.start(registry);
        return new Query() {
            @Override
            public void success() {
                recordQuery(sample, "success");
            }

            @Override
            public void unavailable() {
                recordQuery(sample, "unavailable");
            }

            @Override
            public void failure() {
                recordQuery(sample, "failure");
            }
        };
    }

    private void incrementRecord(String outcome) {
        safely(() -> registry.counter(RECORD, "outcome", outcome).increment());
    }

    private void recordQuery(Timer.Sample sample, String outcome) {
        safely(() -> registry.counter(QUERY, "outcome", outcome).increment());
        safely(() -> sample.stop(registry.timer(QUERY_DURATION, "outcome", outcome)));
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Metrics must not change popular-term behavior.
        }
    }
}
