package com.econpulse.mapping.application;

import com.econpulse.mapping.domain.TermNewsMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MappingApplicationConfig {

    @Bean
    public TermNewsMatcher termNewsMatcher() {
        return new TermNewsMatcher();
    }
}
