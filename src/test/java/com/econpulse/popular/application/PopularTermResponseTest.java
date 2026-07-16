package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PopularTermResponseTest {

    @Test
    void rejectsInvalidFields() {
        assertThatThrownBy(() -> new PopularTermResponse(0, 1, "name", "description", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermResponse(1, 0, "name", "description", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermResponse(1, 1, " ", "description", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermResponse(1, 1, "name", " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermResponse(1, 1, "name", "description", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
