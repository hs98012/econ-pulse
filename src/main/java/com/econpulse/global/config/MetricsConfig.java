package com.econpulse.global.config;

import com.econpulse.mapping.application.TermNewsMappingMetrics;
import com.econpulse.mapping.infrastructure.metrics.MicrometerTermNewsMappingMetrics;
import com.econpulse.news.application.port.NewsIngestionMetrics;
import com.econpulse.news.application.port.NewsProviderMetrics;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsIngestionMetrics;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsProviderMetrics;
import com.econpulse.popular.application.PopularTermMetrics;
import com.econpulse.popular.infrastructure.metrics.MicrometerPopularTermMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public NewsIngestionMetrics newsIngestionMetrics(MeterRegistry registry) {
        return new MicrometerNewsIngestionMetrics(registry);
    }

    @Bean
    public NewsProviderMetrics newsProviderMetrics(MeterRegistry registry) {
        return new MicrometerNewsProviderMetrics(registry);
    }

    @Bean
    public TermNewsMappingMetrics termNewsMappingMetrics(MeterRegistry registry) {
        return new MicrometerTermNewsMappingMetrics(registry);
    }

    @Bean
    public PopularTermMetrics popularTermMetrics(MeterRegistry registry) {
        return new MicrometerPopularTermMetrics(registry);
    }
}
