package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecordTermSearchCommandTest {

    @Test
    void acceptsPositiveEconomicTermId() {
        assertThat(new RecordTermSearchCommand(1L).economicTermId()).isEqualTo(1L);
    }

    @Test
    void rejectsNullOrNonPositiveEconomicTermId() {
        assertThatThrownBy(() -> new RecordTermSearchCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordTermSearchCommand(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordTermSearchCommand(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
