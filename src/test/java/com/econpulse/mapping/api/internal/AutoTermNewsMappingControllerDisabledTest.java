package com.econpulse.mapping.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.mapping.application.TermNewsAutoMappingService;
import com.econpulse.news.api.NewsController;
import com.econpulse.news.api.internal.NewsSyncController;
import com.econpulse.news.application.NewsIngestionService;
import com.econpulse.news.application.NewsQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AutoTermNewsMappingControllerDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    AutoTermNewsMappingController.class,
                    NewsSyncController.class,
                    NewsController.class,
                    MockServiceConfig.class
            );

    @Test
    void controllerIsAbsentWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(AutoTermNewsMappingController.class));
    }

    @Test
    void controllerIsAbsentWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("econpulse.internal.term-news-mapping.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AutoTermNewsMappingController.class));
    }

    @Test
    void controllerIsPresentOnlyWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("econpulse.internal.term-news-mapping.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(AutoTermNewsMappingController.class));
    }

    @Test
    void toggleIsIndependentFromNewsSyncAndPublicNewsController() {
        contextRunner
                .withPropertyValues(
                        "econpulse.internal.term-news-mapping.enabled=false",
                        "econpulse.internal.news-sync.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AutoTermNewsMappingController.class);
                    assertThat(context).hasSingleBean(NewsSyncController.class);
                    assertThat(context).hasSingleBean(NewsController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockServiceConfig {

        @Bean
        TermNewsAutoMappingService termNewsAutoMappingService() {
            return Mockito.mock(TermNewsAutoMappingService.class);
        }

        @Bean
        NewsIngestionService newsIngestionService() {
            return Mockito.mock(NewsIngestionService.class);
        }

        @Bean
        NewsQueryService newsQueryService() {
            return Mockito.mock(NewsQueryService.class);
        }
    }
}
