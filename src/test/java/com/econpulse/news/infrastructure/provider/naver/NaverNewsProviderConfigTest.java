package com.econpulse.news.infrastructure.provider.naver;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import com.econpulse.news.infrastructure.provider.LocalFakeNewsProviderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NaverNewsProviderConfigTest {

    private static final String CLIENT_ID = "configuration-client-id";
    private static final String CLIENT_SECRET = "configuration-client-secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(NaverNewsProviderConfig.class);

    @Test
    void registersNaverProviderAndBindsConfigurationWhenSelected() {
        contextRunner.withPropertyValues(naverProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(NewsProvider.class);
                    assertThat(context.getBean(NewsProvider.class)).isInstanceOf(NaverNewsProvider.class);
                    NaverNewsProperties properties = context.getBean(NaverNewsProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://provider.example");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(400));
                });
    }

    @Test
    void doesNotRegisterNaverProviderForFakeOrMissingType() {
        contextRunner.withPropertyValues("econpulse.news.provider.type=fake")
                .run(context -> assertThat(context).doesNotHaveBean(NewsProvider.class));
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(NewsProvider.class));
    }

    @Test
    void missingCredentialsFailWithoutExposingConfiguredSecret() {
        contextRunner.withPropertyValues(
                        "econpulse.news.provider.type=naver",
                        "econpulse.news.naver.base-url=https://provider.example",
                        "econpulse.news.naver.client-secret=" + CLIENT_SECRET,
                        "econpulse.news.naver.connect-timeout=250ms",
                        "econpulse.news.naver.read-timeout=400ms"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageNotContaining(CLIENT_SECRET);
                });

        contextRunner.withPropertyValues(
                        "econpulse.news.provider.type=naver",
                        "econpulse.news.naver.base-url=https://provider.example",
                        "econpulse.news.naver.client-id=" + CLIENT_ID,
                        "econpulse.news.naver.connect-timeout=250ms",
                        "econpulse.news.naver.read-timeout=400ms"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageNotContaining(CLIENT_ID);
                });
    }

    @Test
    void localProfileNeverRegistersFakeAndNaverProvidersTogether() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(LocalFakeNewsProviderConfig.class, NaverNewsProviderConfig.class)
                .withPropertyValues(naverProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(NewsProvider.class);
                    assertThat(context).doesNotHaveBean(FakeNewsProvider.class);
                    assertThat(context.getBean(NewsProvider.class)).isInstanceOf(NaverNewsProvider.class);
                });
    }

    private String[] naverProperties() {
        return new String[]{
                "econpulse.news.provider.type=naver",
                "econpulse.news.naver.base-url=https://provider.example",
                "econpulse.news.naver.client-id=" + CLIENT_ID,
                "econpulse.news.naver.client-secret=" + CLIENT_SECRET,
                "econpulse.news.naver.connect-timeout=250ms",
                "econpulse.news.naver.read-timeout=400ms"
        };
    }
}
