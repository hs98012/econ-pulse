package com.econpulse.mapping.infrastructure.metrics;

import com.econpulse.mapping.application.AutoMapNewsResult;
import com.econpulse.mapping.application.TermNewsMappingMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class MicrometerTermNewsMappingMetrics implements TermNewsMappingMetrics {

    static final String RUNS = "econpulse.term_news.mapping.runs";
    static final String DURATION = "econpulse.term_news.mapping.duration";
    static final String RESULTS = "econpulse.term_news.mapping.results";

    private final MeterRegistry registry;

    public MicrometerTermNewsMappingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Run start() {
        Timer.Sample sample = Timer.start(registry);
        return new Run() {
            @Override
            public void success(AutoMapNewsResult result) {
                Runnable recorder = () -> recordSuccess(sample, result);
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_COMMITTED) {
                                recorder.run();
                            } else {
                                recordOutcome(sample, "failure");
                            }
                        }
                    });
                } else {
                    recorder.run();
                }
            }

            @Override
            public void failure() {
                recordOutcome(sample, "failure");
            }
        };
    }

    private void recordSuccess(Timer.Sample sample, AutoMapNewsResult result) {
        safely(() -> {
            registry.counter(RESULTS, "result", "created").increment(result.created());
            registry.counter(RESULTS, "result", "updated").increment(result.updated());
            registry.counter(RESULTS, "result", "skipped").increment(result.skipped());
            registry.counter(RESULTS, "result", "no_match").increment(result.noMatch());
        });
        recordOutcome(sample, "success");
    }

    private void recordOutcome(Timer.Sample sample, String outcome) {
        safely(() -> registry.counter(RUNS, "outcome", outcome).increment());
        safely(() -> sample.stop(registry.timer(DURATION, "outcome", outcome)));
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Metrics must not change mapping behavior.
        }
    }
}
