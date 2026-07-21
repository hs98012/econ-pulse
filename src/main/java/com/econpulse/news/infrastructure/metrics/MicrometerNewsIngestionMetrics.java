package com.econpulse.news.infrastructure.metrics;

import com.econpulse.news.application.NewsIngestionResult;
import com.econpulse.news.application.port.NewsIngestionMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class MicrometerNewsIngestionMetrics implements NewsIngestionMetrics {

    static final String RUNS = "econpulse.news.ingestion.runs";
    static final String DURATION = "econpulse.news.ingestion.duration";
    static final String ARTICLES = "econpulse.news.ingestion.articles";

    private final MeterRegistry registry;

    public MicrometerNewsIngestionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Run start() {
        Timer.Sample sample = Timer.start(registry);
        return new Run() {
            @Override
            public void success(NewsIngestionResult result) {
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

    private void recordSuccess(Timer.Sample sample, NewsIngestionResult result) {
        safely(() -> {
            registry.counter(ARTICLES, "result", "fetched").increment(result.fetched());
            registry.counter(ARTICLES, "result", "created").increment(result.created());
            registry.counter(ARTICLES, "result", "updated").increment(result.updated());
            registry.counter(ARTICLES, "result", "skipped").increment(result.skipped());
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
            // Metrics must not change ingestion behavior.
        }
    }
}
