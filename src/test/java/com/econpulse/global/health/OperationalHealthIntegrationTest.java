package com.econpulse.global.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.global.error.GlobalExceptionHandler;
import com.econpulse.mapping.application.TermNewsMappingMetrics;
import com.econpulse.mapping.infrastructure.metrics.MicrometerTermNewsMappingMetrics;
import com.econpulse.news.application.port.NewsIngestionMetrics;
import com.econpulse.news.infrastructure.metrics.MicrometerNewsIngestionMetrics;
import com.econpulse.popular.api.PopularTermController;
import com.econpulse.popular.application.PopularTermMetrics;
import com.econpulse.popular.infrastructure.metrics.MicrometerPopularTermMetrics;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.api.EconomicTermController;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OperationalHealthIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final MockMvc mockMvc;
    private final HealthEndpoint healthEndpoint;
    private final HealthEndpointGroups healthEndpointGroups;
    private final HealthContributorRegistry healthContributorRegistry;
    private final EconomicTermController economicTermController;
    private final PopularTermController popularTermController;
    private final GlobalExceptionHandler globalExceptionHandler;
    private final Environment environment;
    private final MeterRegistry meterRegistry;
    private final NewsIngestionMetrics newsIngestionMetrics;
    private final TermNewsMappingMetrics termNewsMappingMetrics;
    private final PopularTermMetrics popularTermMetrics;

    @Autowired
    OperationalHealthIntegrationTest(
            MockMvc mockMvc,
            HealthEndpoint healthEndpoint,
            HealthEndpointGroups healthEndpointGroups,
            HealthContributorRegistry healthContributorRegistry,
            EconomicTermController economicTermController,
            PopularTermController popularTermController,
            GlobalExceptionHandler globalExceptionHandler,
            Environment environment,
            MeterRegistry meterRegistry,
            NewsIngestionMetrics newsIngestionMetrics,
            TermNewsMappingMetrics termNewsMappingMetrics,
            PopularTermMetrics popularTermMetrics
    ) {
        this.mockMvc = mockMvc;
        this.healthEndpoint = healthEndpoint;
        this.healthEndpointGroups = healthEndpointGroups;
        this.healthContributorRegistry = healthContributorRegistry;
        this.economicTermController = economicTermController;
        this.popularTermController = popularTermController;
        this.globalExceptionHandler = globalExceptionHandler;
        this.environment = environment;
        this.meterRegistry = meterRegistry;
        this.newsIngestionMetrics = newsIngestionMetrics;
        this.termNewsMappingMetrics = termNewsMappingMetrics;
        this.popularTermMetrics = popularTermMetrics;
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void actuatorAndExistingApiInfrastructureBeansAreRegistered() {
        assertThat(healthEndpoint).isNotNull();
        assertThat(economicTermController).isNotNull();
        assertThat(popularTermController).isNotNull();
        assertThat(globalExceptionHandler).isNotNull();
        assertThat(healthContributorRegistry.getContributor("db")).isNotNull();
        assertThat(healthContributorRegistry.getContributor("redis")).isNotNull();
        assertThat(healthContributorRegistry.getContributor("naver")).isNull();
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo("logstash");
        assertThat(meterRegistry).isNotNull();
        assertThat(newsIngestionMetrics).isInstanceOf(MicrometerNewsIngestionMetrics.class);
        assertThat(termNewsMappingMetrics).isInstanceOf(MicrometerTermNewsMappingMetrics.class);
        assertThat(popularTermMetrics).isInstanceOf(MicrometerPopularTermMetrics.class);
    }

    @Test
    void healthGroupsSeparateProcessLivenessFromRequiredDependencies() {
        assertThat(healthEndpointGroups.getNames()).contains("liveness", "readiness");
        HealthEndpointGroup liveness = healthEndpointGroups.get("liveness");
        HealthEndpointGroup readiness = healthEndpointGroups.get("readiness");

        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(liveness.isMember("db")).isFalse();
        assertThat(liveness.isMember("redis")).isFalse();
        assertThat(readiness.isMember("readinessState")).isTrue();
        assertThat(readiness.isMember("db")).isTrue();
        assertThat(readiness.isMember("redis")).isTrue();
        assertThat(readiness.isMember("naver")).isFalse();
    }

    @Test
    void healthyMysqlAndRedisReturnSanitizedUpResponses() throws Exception {
        assertStatusOnly("/actuator/health", "UP");
        assertStatusOnly("/actuator/health/liveness", "UP");
        assertStatusOnly("/actuator/health/readiness", "UP");

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void onlyHealthAndInfoAreExposed() throws Exception {
        assertNotExposed("env");
        assertNotExposed("beans");
        assertNotExposed("configprops");
        assertNotExposed("heapdump");
        assertNotExposed("threaddump");
        assertNotExposed("loggers");
        assertNotExposed("mappings");
        assertNotExposed("scheduledtasks");
        assertNotExposed("conditions");
        assertNotExposed("metrics");
        assertNotExposed("prometheus");
    }

    @Test
    void redisDownMakesReadiness503WhileLivenessStaysUpAndDetailsStayHidden() throws Exception {
        assertDependencyDownPolicy("redis", "redis://secret-host:6379");
    }

    @Test
    void mysqlDownMakesReadiness503WhileLivenessStaysUpAndDetailsStayHidden() throws Exception {
        assertDependencyDownPolicy("db", "jdbc:mysql://secret-host:3306/econpulse?user=secret-user");
    }

    private void assertDependencyDownPolicy(String contributorName, String secretDetail) throws Exception {
        HealthContributor original = healthContributorRegistry.unregisterContributor(contributorName);
        assertThat(original).isNotNull();
        HealthIndicator down = () -> Health.down().withDetail("error", secretDetail).build();
        healthContributorRegistry.registerContributor(contributorName, down);
        try {
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"status\":\"UP\"}"));
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("{\"status\":\"DOWN\"}"))
                    .andExpect(content().string(not(containsString("secret-host"))))
                    .andExpect(content().string(not(containsString("secret-user"))));
        } finally {
            healthContributorRegistry.unregisterContributor(contributorName);
            healthContributorRegistry.registerContributor(contributorName, original);
        }
    }

    private void assertStatusOnly(String path, String expectedStatus) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("redis"))))
                .andExpect(content().string(not(containsString("naver"))));
    }

    private void assertNotExposed(String endpoint) throws Exception {
        mockMvc.perform(get("/actuator/" + endpoint))
                .andExpect(status().isNotFound());
    }
}
