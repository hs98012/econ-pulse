package com.econpulse.news.infrastructure.metrics;

import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MicrometerNewsProviderMetrics implements NewsProviderMetrics {

    static final String REQUESTS = "econpulse.news.provider.requests";
    static final String DURATION = "econpulse.news.provider.duration";

    private final MeterRegistry registry;

    public MicrometerNewsProviderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Request startRequest() {
        Timer.Sample sample = Timer.start(registry);
        return new Request() {
            @Override
            public void success() {
                record(sample, "success", "none");
            }

            @Override
            public void failure(NewsProviderErrorType errorType) {
                record(sample, "failure", errorTag(errorType));
            }
        };
    }

    private void record(Timer.Sample sample, String outcome, String error) {
        safely(() -> registry.counter(
                REQUESTS,
                "provider", "naver",
                "outcome", outcome,
                "error", error
        ).increment());
        safely(() -> sample.stop(registry.timer(DURATION, "provider", "naver", "outcome", outcome)));
    }

    private String errorTag(NewsProviderErrorType errorType) {
        return switch (errorType) {
            case AUTHENTICATION_FAILED -> "authentication";
            case RATE_LIMITED -> "rate_limit";
            case TIMEOUT -> "timeout";
            case TEMPORARY_FAILURE -> "temporary_failure";
            case INVALID_RESPONSE -> "bad_response";
            case INVALID_REQUEST -> "invalid_request";
        };
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Metrics must not change provider behavior.
        }
    }
}
