package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PopularTermQueryTest {

    @Test
    void acceptsExplicitDateAndValidLimit() {
        PopularTermQuery query = new PopularTermQuery(LocalDate.parse("2026-07-16"), 100);

        assertThat(query.date()).isEqualTo(LocalDate.parse("2026-07-16"));
        assertThat(query.limit()).isEqualTo(100);
    }

    @Test
    void rejectsNullDateAndOutOfRangeLimit() {
        LocalDate date = LocalDate.parse("2026-07-16");

        assertThatThrownBy(() -> new PopularTermQuery(null, 10))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PopularTermQuery(date, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermQuery(date, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermQuery(date, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
