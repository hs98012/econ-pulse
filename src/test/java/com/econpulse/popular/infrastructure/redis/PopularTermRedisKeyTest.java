package com.econpulse.popular.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PopularTermRedisKeyTest {

    private final PopularTermRedisKey key = new PopularTermRedisKey("econpulse:popular-terms");

    @Test
    void createsIsoDailyKeys() {
        assertThat(key.daily(LocalDate.parse("2026-07-16")))
                .isEqualTo("econpulse:popular-terms:2026-07-16");
        assertThat(key.daily(LocalDate.parse("2026-07-17")))
                .isEqualTo("econpulse:popular-terms:2026-07-17");
    }

    @Test
    void rejectsBlankPrefixAndNullDate() {
        assertThatThrownBy(() -> new PopularTermRedisKey(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> key.daily(null)).isInstanceOf(NullPointerException.class);
    }
}
