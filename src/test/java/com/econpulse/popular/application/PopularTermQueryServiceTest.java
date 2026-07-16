package com.econpulse.popular.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.popular.application.port.PopularTermStoreException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PopularTermQueryServiceTest {

    private static final LocalDate DATE = LocalDate.parse("2026-07-16");

    private final PopularTermStore store = mock(PopularTermStore.class);
    private final EconomicTermRepository repository = mock(EconomicTermRepository.class);
    private final PopularTermQueryService service = new PopularTermQueryService(store, repository);

    @Test
    void rejectsInvalidQueriesBeforeDependenciesAreCalled() {
        assertThatThrownBy(() -> service.findPopularTerms(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermQuery(null, 10))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PopularTermQuery(DATE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermQuery(DATE, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PopularTermQuery(DATE, 101))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(store, repository);
    }

    @Test
    void skipsMysqlWhenRedisRankingIsEmpty() {
        when(store.findTop(DATE, 10)).thenReturn(List.of());

        assertThat(service.findPopularTerms(new PopularTermQuery(DATE, 10))).isEmpty();
        verify(repository, never()).findAllByIdInAndStatus(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void joinsInOneQueryAndPreservesRedisOrderDespiteMysqlOrder() {
        when(store.findTop(DATE, 3)).thenReturn(List.of(
                new PopularTermScore(12, 25, 1),
                new PopularTermScore(4, 18, 2),
                new PopularTermScore(31, 18, 3)
        ));
        EconomicTerm first = term(12, "기준금리", "기준금리 정의");
        EconomicTerm second = term(4, "GDP", "GDP 정의");
        EconomicTerm third = term(31, "CPI", "CPI 정의");
        when(repository.findAllByIdInAndStatus(any(), eq(TermStatus.ACTIVE)))
                .thenReturn(List.of(third, first, second));

        List<PopularTermResponse> result = service.findPopularTerms(new PopularTermQuery(DATE, 3));

        assertThat(result).containsExactly(
                new PopularTermResponse(1, 12, "기준금리", "기준금리 정의", 25),
                new PopularTermResponse(2, 4, "GDP", "GDP 정의", 18),
                new PopularTermResponse(3, 31, "CPI", "CPI 정의", 18)
        );
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(repository).findAllByIdInAndStatus(ids.capture(), eq(TermStatus.ACTIVE));
        assertThat(ids.getValue()).containsExactly(12L, 4L, 31L);
        assertThatThrownBy(() -> result.add(new PopularTermResponse(4, 1, "x", "y", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void excludesMissingAndInactiveTermsAndRecalculatesRanks() {
        when(store.findTop(DATE, 4)).thenReturn(List.of(
                new PopularTermScore(10, 30, 1),
                new PopularTermScore(20, 20, 2),
                new PopularTermScore(30, 15, 3),
                new PopularTermScore(40, 10, 4)
        ));
        EconomicTerm exchangeRate = term(40, "환율", "환율 정의");
        EconomicTerm gdp = term(20, "GDP", "GDP 정의");
        when(repository.findAllByIdInAndStatus(any(), eq(TermStatus.ACTIVE)))
                .thenReturn(List.of(exchangeRate, gdp));

        assertThat(service.findPopularTerms(new PopularTermQuery(DATE, 4))).containsExactly(
                new PopularTermResponse(1, 20, "GDP", "GDP 정의", 20),
                new PopularTermResponse(2, 40, "환율", "환율 정의", 10)
        );
        verify(store, never()).increment(anyLong(), any());
    }

    @Test
    void propagatesRedisFailureAndDoesNotQueryMysql() {
        PopularTermStoreException failure = new PopularTermStoreException(
                PopularTermStoreException.Reason.UNAVAILABLE,
                "Popular term store is unavailable."
        );
        when(store.findTop(DATE, 10)).thenThrow(failure);

        assertThatThrownBy(() -> service.findPopularTerms(new PopularTermQuery(DATE, 10)))
                .isSameAs(failure);
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotHideMysqlFailure() {
        when(store.findTop(DATE, 1)).thenReturn(List.of(new PopularTermScore(1, 3, 1)));
        RuntimeException failure = new RuntimeException("database failed");
        when(repository.findAllByIdInAndStatus(List.of(1L), TermStatus.ACTIVE)).thenThrow(failure);

        assertThatThrownBy(() -> service.findPopularTerms(new PopularTermQuery(DATE, 1)))
                .isSameAs(failure);
    }

    private EconomicTerm term(long id, String name, String definition) {
        EconomicTerm term = mock(EconomicTerm.class);
        when(term.getId()).thenReturn(id);
        when(term.getName()).thenReturn(name);
        when(term.getDefinition()).thenReturn(definition);
        return term;
    }
}
