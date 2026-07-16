package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TermRelatedNewsQueryServiceTest {

    @Mock
    private EconomicTermRepository termRepository;
    @Mock
    private TermNewsMappingRepository mappingRepository;

    private TermRelatedNewsQueryService service;

    @BeforeEach
    void setUp() {
        service = new TermRelatedNewsQueryService(termRepository, mappingRepository);
    }

    @Test
    void returnsEmptyPageForActiveTermWithoutMappings() {
        EconomicTerm term = new EconomicTerm("GDP", "gdp", "definition", List.of());
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(1L, 0, 20);
        when(termRepository.findByIdAndStatus(1L, TermStatus.ACTIVE)).thenReturn(Optional.of(term));
        when(mappingRepository.findRelatedNewsByEconomicTermId(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = service.find(query);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void missingOrInactiveTermIsNotFoundBeforeMappingQuery() {
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(1L, 0, 20);
        when(termRepository.findByIdAndStatus(1L, TermStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(query)).isInstanceOf(TermNotFoundException.class);
        verify(mappingRepository, never()).findRelatedNewsByEconomicTermId(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsNullQuery() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.find(null));
    }
}
