package com.econpulse.popular.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
class PopularTermApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/terms/popular";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final MockMvc mockMvc;
    private final PopularTermStore store;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final RedisConnectionFactory redisConnectionFactory;
    private final Clock clock;

    @Autowired
    PopularTermApiIntegrationTest(
            MockMvc mockMvc,
            PopularTermStore store,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            RedisConnectionFactory redisConnectionFactory,
            Clock clock
    ) {
        this.mockMvc = mockMvc;
        this.store = store;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.redisConnectionFactory = redisConnectionFactory;
        this.clock = clock;
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

    private void increment(long economicTermId, int count, LocalDate date) {
        for (int index = 0; index < count; index++) {
            store.increment(economicTermId, date);
        }
    }

    private EconomicTerm term(String name, String definition) {
        return new EconomicTerm(name, name.toLowerCase(), definition, List.<EconomicTermAlias>of());
    }
}
