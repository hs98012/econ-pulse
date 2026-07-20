package com.econpulse.popular.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.popular.application.PopularTermScore;
import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.popular.infrastructure.redis.PopularTermRedisKey;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(PopularTermApiIntegrationTest.FixedClockConfig.class)
class PopularTermApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/terms/popular";
    private static final Instant FIXED_NOW = Instant.parse("2026-07-20T08:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final MockMvc mockMvc;
    private final PopularTermStore store;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final RedisConnectionFactory redisConnectionFactory;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final PopularTermRedisKey redisKey;

    @Autowired
    PopularTermApiIntegrationTest(
            MockMvc mockMvc,
            PopularTermStore store,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            RedisConnectionFactory redisConnectionFactory,
            Clock clock,
            StringRedisTemplate redisTemplate,
            PopularTermRedisKey redisKey
    ) {
        this.mockMvc = mockMvc;
        this.store = store;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.redisConnectionFactory = redisConnectionFactory;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.redisKey = redisKey;
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("econpulse.popular-terms.key-prefix", () -> "econpulse:test:popular-api");
    }

    @BeforeEach
    void setUp() {
        aliasRepository.deleteAll();
        termRepository.deleteAll();
        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }

    @Test
    void publicApiJoinsTodayRedisRankingWithActiveMysqlTerms() throws Exception {
        LocalDate today = LocalDate.now(clock);
        EconomicTerm high = termRepository.saveAndFlush(term("기준금리", "기준금리 정의"));
        EconomicTerm tiedLowerId = termRepository.saveAndFlush(term("GDP", "GDP 정의"));
        EconomicTerm tiedHigherId = termRepository.saveAndFlush(term("CPI", "CPI 정의"));
        EconomicTerm inactive = term("환율", "환율 정의");
        inactive.deactivate();
        inactive = termRepository.saveAndFlush(inactive);

        increment(999_999L, 9, today);
        increment(inactive.getId(), 8, today);
        increment(high.getId(), 7, today);
        increment(tiedLowerId.getId(), 5, today);
        increment(tiedHigherId.getId(), 5, today);
        increment(high.getId(), 2, today.minusDays(1));

        mockMvc.perform(get(PATH).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].economicTermId").value(high.getId()))
                .andExpect(jsonPath("$[0].name").value("기준금리"))
                .andExpect(jsonPath("$[0].description").value("기준금리 정의"))
                .andExpect(jsonPath("$[0].score").value(7))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].economicTermId").value(tiedLowerId.getId()))
                .andExpect(jsonPath("$[1].score").value(5))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[2].economicTermId").value(tiedHigherId.getId()))
                .andExpect(jsonPath("$[2].score").value(5));

        mockMvc.perform(get(PATH).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void popularStaticRouteWinsOverNumericTermDetailRouteAndEmptyTodayIsArray() throws Exception {
        increment(1L, 1, LocalDate.now(clock).minusDays(1));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void successfulDetailViewsDriveTodayPopularApiAndKeepTtl() throws Exception {
        LocalDate today = LocalDate.now(clock);
        EconomicTerm first = termRepository.saveAndFlush(term("기준금리", "기준금리 정의"));
        EconomicTerm second = termRepository.saveAndFlush(term("환율", "환율 정의"));

        view(first.getId(), 3);
        view(second.getId(), 1);

        org.assertj.core.api.Assertions.assertThat(store.findTop(today, 10)).containsExactly(
                new PopularTermScore(first.getId(), 3, 1),
                new PopularTermScore(second.getId(), 1, 2)
        );
        org.assertj.core.api.Assertions.assertThat(store.findTop(today.minusDays(1), 10)).isEmpty();
        Long ttl = redisTemplate.getExpire(redisKey.daily(today), TimeUnit.SECONDS);
        org.assertj.core.api.Assertions.assertThat(ttl).isBetween(
                Duration.ofDays(7).minusMinutes(1).toSeconds(),
                Duration.ofDays(7).toSeconds()
        );

        mockMvc.perform(get(PATH).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].economicTermId").value(first.getId()))
                .andExpect(jsonPath("$[0].name").value("기준금리"))
                .andExpect(jsonPath("$[0].description").value("기준금리 정의"))
                .andExpect(jsonPath("$[0].score").value(3));

        mockMvc.perform(get(PATH).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].economicTermId").value(second.getId()))
                .andExpect(jsonPath("$[1].score").value(1));
    }

    @Test
    void equalDetailViewCountsUseExistingTermIdTieBreak() throws Exception {
        EconomicTerm lowerId = termRepository.saveAndFlush(term("GDP", "GDP 정의"));
        EconomicTerm higherId = termRepository.saveAndFlush(term("CPI", "CPI 정의"));

        view(higherId.getId(), 1);
        view(lowerId.getId(), 1);

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].economicTermId").value(lowerId.getId()))
                .andExpect(jsonPath("$[0].score").value(1))
                .andExpect(jsonPath("$[1].economicTermId").value(higherId.getId()))
                .andExpect(jsonPath("$[1].score").value(1));
    }

    @Test
    void missingAndInvalidDetailRequestsDoNotCreateScores() throws Exception {
        LocalDate today = LocalDate.now(clock);
        EconomicTerm inactive = term("비활성 용어", "비활성 정의");
        inactive.deactivate();
        inactive = termRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/terms/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/terms/{termId}", inactive.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/terms/0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/terms/-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/terms/text"))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(store.findTop(today, 10)).isEmpty();
    }

    private void increment(long economicTermId, int count, LocalDate date) {
        for (int index = 0; index < count; index++) {
            store.increment(economicTermId, date);
        }
    }

    private void view(long economicTermId, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            mockMvc.perform(get("/api/v1/terms/{termId}", economicTermId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(economicTermId))
                    .andExpect(jsonPath("$.score").doesNotExist())
                    .andExpect(jsonPath("$.rank").doesNotExist());
        }
    }

    private EconomicTerm term(String name, String definition) {
        return new EconomicTerm(name, name.toLowerCase(), definition, List.<EconomicTermAlias>of());
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
