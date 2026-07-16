package com.econpulse.popular.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.popular.application.PopularTermScore;
import com.econpulse.popular.application.port.PopularTermStoreException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisPopularTermStoreIntegrationTest {

    private static final LocalDate DATE = LocalDate.parse("2026-07-16");
    private static final LocalDate NEXT_DATE = LocalDate.parse("2026-07-17");
    private static final String PREFIX = "econpulse:test:popular-terms";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisPopularTermStore store;
    private static PopularTermRedisKey redisKey;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        PopularTermProperties properties = new PopularTermProperties(PREFIX, Duration.ofDays(7), 100);
        redisKey = new PopularTermRedisKey(PREFIX);
        store = new RedisPopularTermStore(redisTemplate, redisKey, properties);
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void clearRedis() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    @Test
    void incrementsRanksLimitsAndSeparatesDailyKeys() {
        store.increment(2L, DATE);
        store.increment(2L, DATE);
        store.increment(1L, DATE);
        store.increment(3L, DATE);
        store.increment(3L, NEXT_DATE);

        assertThat(store.findTop(DATE, 2)).containsExactly(
                new PopularTermScore(2L, 2L, 1),
                new PopularTermScore(3L, 1L, 2)
        );
        assertThat(store.findTop(NEXT_DATE, 10))
                .containsExactly(new PopularTermScore(3L, 1L, 1));
        assertThat(store.findTop(LocalDate.parse("2026-07-18"), 10)).isEmpty();
        assertThat(redisTemplate.opsForZSet().score(redisKey.daily(DATE), "2")).isEqualTo(2.0);
    }

    @Test
    void ordersEqualScoresByIdWithinFetchedRange() {
        store.increment(1L, DATE);
        store.increment(2L, DATE);
        store.increment(3L, DATE);

        assertThat(store.findTop(DATE, 3)).containsExactly(
                new PopularTermScore(1L, 1L, 1),
                new PopularTermScore(2L, 1L, 2),
                new PopularTermScore(3L, 1L, 3)
        );
    }

    @Test
    void refreshesSevenDayTtlAfterIncrement() {
        store.increment(4L, DATE);

        Long ttl = redisTemplate.getExpire(redisKey.daily(DATE), TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofDays(7).minusMinutes(1).toSeconds(), Duration.ofDays(7).toSeconds());
    }

    @Test
    void rejectsInvalidMembersAndScores() {
        String key = redisKey.daily(DATE);
        redisTemplate.opsForZSet().add(key, "not-an-id", 2.0);

        assertThatThrownBy(() -> store.findTop(DATE, 10))
                .isInstanceOf(PopularTermStoreException.class)
                .extracting("reason")
                .isEqualTo(PopularTermStoreException.Reason.INVALID_DATA);

        redisTemplate.delete(key);
        redisTemplate.opsForZSet().add(key, "1", 1.5);
        assertThatThrownBy(() -> store.findTop(DATE, 10))
                .isInstanceOf(PopularTermStoreException.class)
                .extracting("reason")
                .isEqualTo(PopularTermStoreException.Reason.INVALID_DATA);
    }

    @Test
    void concurrentIncrementsAreAtomic() throws Exception {
        int workers = 8;
        int incrementsPerWorker = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < incrementsPerWorker; index++) {
                        store.increment(9L, DATE);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(store.findTop(DATE, 1))
                .containsExactly(new PopularTermScore(9L, (long) workers * incrementsPerWorker, 1));
    }
}
