package com.econpulse.popular.infrastructure.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PopularTermProperties.class)
public class PopularTermRedisConfig {

    @Bean
    public PopularTermRedisKey popularTermRedisKey(PopularTermProperties properties) {
        return new PopularTermRedisKey(properties.keyPrefix());
    }
}
