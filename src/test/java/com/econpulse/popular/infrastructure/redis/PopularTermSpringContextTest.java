package com.econpulse.popular.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.econpulse.popular.application.PopularTermService;
import com.econpulse.popular.application.port.PopularTermStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

class PopularTermSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestConfig.class,
                    PopularTermRedisConfig.class,
                    RedisPopularTermStore.class,
                    PopularTermService.class
            )
            .withPropertyValues(
                    "econpulse.popular-terms.key-prefix=econpulse:test:popular-terms",
                    "econpulse.popular-terms.retention=7d",
                    "econpulse.popular-terms.max-query-size=100"
            );

    @Test
    void bindsSettingsAndRegistersPortAdapterAndService() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PopularTermProperties.class);
            assertThat(context).hasSingleBean(PopularTermStore.class);
            assertThat(context).hasSingleBean(RedisPopularTermStore.class);
            assertThat(context).hasSingleBean(PopularTermService.class);
            assertThat(context.getBean(PopularTermProperties.class).retention())
                    .isEqualTo(java.time.Duration.ofDays(7));
        });
    }

    @Test
    void failsContextForInvalidSettings() {
        contextRunner.withPropertyValues("econpulse.popular-terms.retention=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
