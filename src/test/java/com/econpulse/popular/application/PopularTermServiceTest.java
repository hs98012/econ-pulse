package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.popular.application.port.PopularTermStoreException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PopularTermServiceTest {

    private static final LocalDate DATE = LocalDate.parse("2026-07-16");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T23:59:59Z"), ZoneOffset.UTC);

    private final PopularTermStore store = mock(PopularTermStore.class);
    private final PopularTermService service = new PopularTermService(store, CLOCK);

    @Test
    void recordsSearchUsingUtcClockDate() {
        service.recordSearch(new RecordTermSearchCommand(7L));

        verify(store).increment(7L, DATE);
    }

    @Test
    void dateChangesWithInjectedClock() {
        PopularTermService nextDayService = new PopularTermService(
                store,
                Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)
        );

        nextDayService.recordSearch(new RecordTermSearchCommand(7L));

        verify(store).increment(7L, LocalDate.parse("2026-07-17"));
    }

    @Test
    void returnsDefensiveImmutableRankingAndEmptyResult() {
        PopularTermQuery query = new PopularTermQuery(DATE, 10);
        ArrayList<PopularTermScore> stored = new ArrayList<>(List.of(new PopularTermScore(2, 4, 1)));
        when(store.findTop(DATE, 10)).thenReturn(stored);

        List<PopularTermScore> result = service.findPopularTerms(query);
        stored.clear();

        assertThat(result).containsExactly(new PopularTermScore(2, 4, 1));
        assertThatThrownBy(() -> result.add(new PopularTermScore(3, 1, 2)))
                .isInstanceOf(UnsupportedOperationException.class);

        PopularTermQuery emptyQuery = new PopularTermQuery(DATE, 5);
        when(store.findTop(DATE, 5)).thenReturn(List.of());
        assertThat(service.findPopularTerms(emptyQuery)).isEmpty();
    }

    @Test
    void doesNotHideStoreFailure() {
        PopularTermStoreException failure = new PopularTermStoreException(
                PopularTermStoreException.Reason.UNAVAILABLE,
                "Popular term store is unavailable."
        );
        when(store.findTop(DATE, 10)).thenThrow(failure);

        assertThatThrownBy(() -> service.findPopularTerms(new PopularTermQuery(DATE, 10)))
                .isSameAs(failure);
    }

    @Test
    void rejectsNullInputsBeforeStoreCall() {
        assertThatThrownBy(() -> service.recordSearch(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findPopularTerms(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(store);
    }
}
