package com.econpulse.news.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.NewsIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NewsSyncControllerDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NewsSyncController.class, MockServiceConfig.class);

    @Test
    void controllerIsAbsentWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(NewsSyncController.class));
    }

    @Test
    void controllerIsAbsentWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("econpulse.internal.news-sync.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NewsSyncController.class));
    }

    @Test
    void controllerIsPresentOnlyWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("econpulse.internal.news-sync.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NewsSyncController.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MockServiceConfig {

        @Bean
        NewsIngestionService newsIngestionService() {
            return Mockito.mock(NewsIngestionService.class);
        }
    }
}
