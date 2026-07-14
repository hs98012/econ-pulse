package com.econpulse.news.infrastructure.provider.naver;

import com.econpulse.news.application.port.NewsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "econpulse.news.provider.type", havingValue = "naver")
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsProviderConfig {

    @Bean
    public NewsProvider naverNewsProvider(NaverNewsProperties properties, ObjectMapper objectMapper) {
        return new NaverNewsProvider(properties, objectMapper);
    }
}
