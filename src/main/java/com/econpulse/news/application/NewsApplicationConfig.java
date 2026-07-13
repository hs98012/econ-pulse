package com.econpulse.news.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NewsApplicationConfig {

    @Bean
    public NewsUrlHasher newsUrlHasher() {
        return new NewsUrlHasher();
    }
}
