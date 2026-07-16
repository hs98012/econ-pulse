package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
    void mapsRelatedNewsEvidenceAndPageMetadata() {
        EconomicTerm term = new EconomicTerm("GDP", "gdp", "definition", List.of());
        TermRelatedNewsQuery query = new TermRelatedNewsQuery(1L, 1, 2);
        PageRequest pageable = PageRequest.of(1, 2);
        TermNewsMapping mapping = mapping(term, article(7L));
        when(termRepository.findByIdAndStatus(1L, TermStatus.ACTIVE)).thenReturn(Optional.of(term));
        when(mappingRepository.findRelatedNewsByEconomicTermId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(mapping), pageable, 5));

        var result = service.find(query);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.content()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.title()).isEqualTo("GDP 전망");
            assertThat(response.summary()).isEqualTo("성장률 전망");
            assertThat(response.sourceName()).isEqualTo("source");
            assertThat(response.sourceUrl()).isEqualTo("https://example.com/7");
            assertThat(response.publishedAt()).hasToString("2026-07-15T01:00:00Z");
            assertThat(response.matchType()).isEqualTo(MatchType.EXACT_NAME);
            assertThat(response.confidenceScore()).isEqualByComparingTo("1.0000");
            assertThat(response.confidenceScore().scale()).isEqualTo(4);
            assertThat(response.matchedAt()).hasToString("2026-07-15T02:00:00Z");
        });
        assertThatThrownBy(() -> result.content().add(result.content().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(mappingRepository).findRelatedNewsByEconomicTermId(1L, pageable);
    }

    @Test
    void rejectsNullQuery() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.find(null));
        verifyNoInteractions(termRepository, mappingRepository);
    }

    private static NewsArticle article(Long id) {
        NewsArticle article = new NewsArticle(
                "GDP 전망",
                "성장률 전망",
                "source",
                "https://example.com/7",
                new byte[32],
                LocalDateTime.parse("2026-07-15T01:00:00"),
                LocalDateTime.parse("2026-07-15T01:30:00")
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private static TermNewsMapping mapping(EconomicTerm term, NewsArticle article) {
        return new TermNewsMapping(
                term,
                article,
                MatchType.EXACT_NAME,
                new BigDecimal("1.0000"),
                LocalDateTime.parse("2026-07-15T02:00:00")
        );
    }
}
