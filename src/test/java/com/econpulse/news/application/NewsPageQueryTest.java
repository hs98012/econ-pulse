package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NewsPageQueryTest {

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> new NewsPageQuery(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroSize() {
        assertThatThrownBy(() -> new NewsPageQuery(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSize() {
        assertThatThrownBy(() -> new NewsPageQuery(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSizeOverMaximum() {
        assertThatThrownBy(() -> new NewsPageQuery(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsValidQueryAndDefaults() {
        assertThat(new NewsPageQuery(2, 100)).isEqualTo(new NewsPageQuery(2, 100));
        assertThat(NewsPageQuery.defaults()).isEqualTo(new NewsPageQuery(0, 20));
    }
}
