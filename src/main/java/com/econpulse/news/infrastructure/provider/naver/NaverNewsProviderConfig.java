package com.econpulse.news.infrastructure.provider.naver;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
@ConditionalOnProperty(name = "econpulse.news.provider.type", havingValue = "naver")
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsProviderConfig {

    @Bean
    public NewsProvider naverNewsProvider(
            NaverNewsProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<NewsProviderMetrics> metrics
    ) {
        return new NaverNewsProvider(properties, objectMapper, metrics.getIfAvailable(() -> NewsProviderMetrics.NO_OP));
    }
}
