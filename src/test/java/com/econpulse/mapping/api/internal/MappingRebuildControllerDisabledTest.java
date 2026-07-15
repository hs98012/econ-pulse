package com.econpulse.mapping.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.mapping.application.TermNewsAutoMappingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MappingRebuildControllerDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MappingRebuildController.class, MockServiceConfig.class);

    @Test
    void controllerIsAbsentWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MappingRebuildController.class));
    }

    @Test
    void controllerIsAbsentWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("econpulse.internal.mapping-rebuild.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MappingRebuildController.class));
    }

    @Test
    void controllerIsPresentOnlyWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("econpulse.internal.mapping-rebuild.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MappingRebuildController.class));
    }

    @Test
    void mappingToggleIsIndependentFromNewsSyncToggle() {
        contextRunner
                .withPropertyValues(
                        "econpulse.internal.mapping-rebuild.enabled=true",
                        "econpulse.internal.news-sync.enabled=false"
                )
                .run(context -> assertThat(context).hasSingleBean(MappingRebuildController.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MockServiceConfig {

        @Bean
        TermNewsAutoMappingService termNewsAutoMappingService() {
            return Mockito.mock(TermNewsAutoMappingService.class);
        }
    }
}
