package com.econpulse.news.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalFakeNewsProviderConfigTest {

    @Test
    void fakeProviderBeanIsAbsentWithoutLocalProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalFakeNewsProviderConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(NewsProvider.class));
    }

    @Test
    void fakeProviderBeanIsAvailableWithLocalProfile() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withUserConfiguration(LocalFakeNewsProviderConfig.class)
                .run(context -> assertThat(context).hasSingleBean(NewsProvider.class));
    }
}
