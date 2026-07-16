package com.econpulse.popular.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.econpulse.popular.application.port.PopularTermStoreException;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class RedisPopularTermStoreTest {

    @Test
    void convertsRedisDataAccessFailureWithoutExposingConnectionDetails() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> operations = mock(ZSetOperations.class);
        when(template.opsForZSet()).thenReturn(operations);
        when(operations.incrementScore("test:2026-07-16", "1", 1.0))
                .thenThrow(new DataAccessResourceFailureException("redis://secret-host:6379"));
        RedisPopularTermStore store = new RedisPopularTermStore(
                template,
                new PopularTermRedisKey("test"),
                new PopularTermProperties("test", Duration.ofDays(7), 100)
        );

        assertThatThrownBy(() -> store.increment(1L, LocalDate.parse("2026-07-16")))
                .isInstanceOf(PopularTermStoreException.class)
                .hasMessage("Popular term store is unavailable.")
                .extracting("reason")
                .isEqualTo(PopularTermStoreException.Reason.UNAVAILABLE);
    }
}
