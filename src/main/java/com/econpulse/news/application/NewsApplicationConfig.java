package com.econpulse.news.application;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsIngestionMetrics;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class NewsApplicationConfig {

    @Bean
    public NewsUrlHasher newsUrlHasher() {
        return new NewsUrlHasher();
    }

    @Bean
    @ConditionalOnProperty(name = "econpulse.internal.news-sync.enabled", havingValue = "true")
    public NewsIngestionService newsIngestionService(
            NewsProvider newsProvider,
            NewsArticleRepository newsArticleRepository,
            NewsUrlHasher newsUrlHasher,
            Clock clock,
            ObjectProvider<NewsIngestionMetrics> metrics
    ) {
        return new NewsIngestionService(
                newsProvider,
                newsArticleRepository,
                newsUrlHasher,
                clock,
                metrics.getIfAvailable(() -> NewsIngestionMetrics.NO_OP)
        );
    }
}
