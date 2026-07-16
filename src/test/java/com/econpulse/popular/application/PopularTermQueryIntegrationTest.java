package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PopularTermQueryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.parse("2026-07-16");
    private static final LocalDate NEXT_DATE = LocalDate.parse("2026-07-17");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final PopularTermQueryService queryService;
    private final PopularTermStore store;
    private final EconomicTermRepository termRepository;
    private final EconomicTermAliasRepository aliasRepository;
    private final RedisConnectionFactory redisConnectionFactory;

    @Autowired
    PopularTermQueryIntegrationTest(
            PopularTermQueryService queryService,
            PopularTermStore store,
            EconomicTermRepository termRepository,
            EconomicTermAliasRepository aliasRepository,
            RedisConnectionFactory redisConnectionFactory
    ) {
        this.queryService = queryService;
        this.store = store;
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("econpulse.popular-terms.key-prefix", () -> "econpulse:test:popular-query");
    }

    @BeforeEach
    void setUp() {
        aliasRepository.deleteAll();
        termRepository.deleteAll();
        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }

    @Test
    void joinsRedisRankingWithActiveMysqlTermsAndAppliesChoiceALimit() {
        EconomicTerm high = termRepository.saveAndFlush(term("기준금리", "기준금리 정의"));
        EconomicTerm tiedLowerId = termRepository.saveAndFlush(term("GDP", "GDP 정의"));
        EconomicTerm tiedHigherId = termRepository.saveAndFlush(term("CPI", "CPI 정의"));
        EconomicTerm inactive = term("환율", "환율 정의");
        inactive.deactivate();
        inactive = termRepository.saveAndFlush(inactive);

        increment(999_999L, 5, DATE);
        increment(inactive.getId(), 4, DATE);
        increment(high.getId(), 3, DATE);
        increment(tiedLowerId.getId(), 2, DATE);
        increment(tiedHigherId.getId(), 2, DATE);

        assertThat(queryService.findPopularTerms(new PopularTermQuery(DATE, 10))).containsExactly(
                new PopularTermResponse(1, high.getId(), "기준금리", "기준금리 정의", 3),
                new PopularTermResponse(2, tiedLowerId.getId(), "GDP", "GDP 정의", 2),
                new PopularTermResponse(3, tiedHigherId.getId(), "CPI", "CPI 정의", 2)
        );
        assertThat(queryService.findPopularTerms(new PopularTermQuery(DATE, 2))).isEmpty();
        assertThat(queryService.findPopularTerms(new PopularTermQuery(NEXT_DATE, 10))).isEmpty();
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
