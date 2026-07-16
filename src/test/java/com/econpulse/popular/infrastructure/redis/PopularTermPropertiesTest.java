package com.econpulse.popular.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PopularTermPropertiesTest {

    @Test
    void acceptsValidSettings() {
        PopularTermProperties properties = new PopularTermProperties("test:popular", Duration.ofDays(7), 100);

        assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new PopularTermProperties(" ", Duration.ofDays(7), 100))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PopularTermProperties("test", Duration.ZERO, 100))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PopularTermProperties("test", Duration.ofDays(7), 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PopularTermProperties("test", Duration.ofDays(7), 101))
                .isInstanceOf(IllegalStateException.class);
    }
}
