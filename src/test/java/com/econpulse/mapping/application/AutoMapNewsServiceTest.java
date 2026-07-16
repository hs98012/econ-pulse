package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.NewsMatchContent;
import com.econpulse.mapping.domain.TermMatchCandidate;
import com.econpulse.mapping.domain.TermMatchTarget;
import com.econpulse.mapping.domain.TermNewsMatcher;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoMapNewsServiceTest {

    @Mock
    private EconomicTermRepository termRepository;
    @Mock
    private NewsArticleRepository articleRepository;
    @Mock
    private TermNewsMappingService mappingService;

    private TermNewsAutoMappingService service;

    @BeforeEach
    void setUp() {
        service = new TermNewsAutoMappingService(
                termRepository,
                articleRepository,
                new TermNewsMatcher(),
                mappingService
        );
    }

    @Test
    void rejectsNullCommandBeforeRepositoryCalls() {
        assertThatThrownBy(() -> service.mapNews(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(articleRepository, termRepository, mappingService);
    }

    @Test
    void stopsWhenNewsDoesNotExist() {
        when(articleRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mapNews(new AutoMapNewsCommand(10L)))
                .isInstanceOf(NewsNotFoundException.class);

        verify(termRepository, never()).findAllByStatusOrderByIdAsc(any());
        verifyNoInteractions(mappingService);
    }

    @Test
    void returnsZeroCountsWhenThereAreNoActiveTerms() {
        prepare(article(10L, "GDP 전망", null), List.of());

        AutoMapNewsResult result = service.mapNews(new AutoMapNewsCommand(10L));

        assertThat(result).isEqualTo(new AutoMapNewsResult(10L, 0, 0, 0, 0, 0, 0));
        verify(termRepository).findAllByStatusOrderByIdAsc(TermStatus.ACTIVE);
        verifyNoInteractions(mappingService);
    }

    @Test
    void convertsExactAndAliasCandidatesAndCountsNoMatch() {
        List<EconomicTerm> terms = List.of(
                term(1L, "GDP", "gdp", List.of()),
                term(2L, "소비자물가지수", "소비자물가지수", List.of(alias("CPI", "cpi"))),
                term(3L, "환율", "환율", List.of())
        );
        prepare(article(10L, "GDP와 CPI 상승", null), terms);
        when(mappingService.save(any()))
                .thenReturn(
                        mappingResult(1L, MatchType.EXACT_NAME, MappingSaveStatus.CREATED),
                        mappingResult(2L, MatchType.ALIAS, MappingSaveStatus.SKIPPED)
                );

        AutoMapNewsResult result = service.mapNews(new AutoMapNewsCommand(10L));

        assertThat(result).isEqualTo(new AutoMapNewsResult(10L, 3, 2, 1, 0, 1, 1));
        ArgumentCaptor<TermNewsMappingCommand> captor = ArgumentCaptor.forClass(TermNewsMappingCommand.class);
        verify(mappingService, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).containsExactly(
                new TermNewsMappingCommand(1L, 10L, MatchType.EXACT_NAME, new BigDecimal("1.0000")),
                new TermNewsMappingCommand(2L, 10L, MatchType.ALIAS, new BigDecimal("0.8000"))
        );
    }

    @Test
    void aggregatesCreatedUpdatedAndSkippedResults() {
        prepare(article(10L, "GDP CPI PPI 환율", null), List.of(
                term(1L, "GDP", "gdp", List.of()),
                term(2L, "CPI", "cpi", List.of()),
                term(3L, "PPI", "ppi", List.of()),
                term(4L, "금리", "금리", List.of())
        ));
        when(mappingService.save(any())).thenReturn(
                mappingResult(1L, MatchType.EXACT_NAME, MappingSaveStatus.CREATED),
                mappingResult(2L, MatchType.EXACT_NAME, MappingSaveStatus.UPDATED),
                mappingResult(3L, MatchType.EXACT_NAME, MappingSaveStatus.SKIPPED)
        );

        AutoMapNewsResult result = service.mapNews(new AutoMapNewsCommand(10L));

        assertThat(result).isEqualTo(new AutoMapNewsResult(10L, 4, 3, 1, 1, 1, 1));
    }

    @Test
    void convertsNormalizedNameAndAliasesToPureTarget() {
        TermNewsMatcher matcher = mock(TermNewsMatcher.class);
        service = new TermNewsAutoMappingService(termRepository, articleRepository, matcher, mappingService);
        prepare(article(10L, "시장 전망", null), List.of(term(
                1L,
                "국내총생산",
                "국내총생산",
                List.of(alias("GDP", "gdp"), alias("국내 총생산", "국내 총생산"))
        )));
        when(matcher.match(any(), any())).thenReturn(Optional.empty());

        service.mapNews(new AutoMapNewsCommand(10L));

        ArgumentCaptor<TermMatchTarget> targetCaptor = ArgumentCaptor.forClass(TermMatchTarget.class);
        ArgumentCaptor<NewsMatchContent> contentCaptor = ArgumentCaptor.forClass(NewsMatchContent.class);
        verify(matcher).match(targetCaptor.capture(), contentCaptor.capture());
        assertThat(targetCaptor.getValue()).isEqualTo(
                new TermMatchTarget(1L, "국내총생산", List.of("gdp", "국내 총생산"))
        );
        assertThat(contentCaptor.getValue()).isEqualTo(new NewsMatchContent(10L, "시장 전망", null));
    }

    @Test
    void propagatesMatcherFailureWithoutSaving() {
        TermNewsMatcher matcher = mock(TermNewsMatcher.class);
        service = new TermNewsAutoMappingService(termRepository, articleRepository, matcher, mappingService);
        prepare(article(10L, "GDP", null), List.of(term(1L, "GDP", "gdp", List.of())));
        when(matcher.match(any(), any())).thenThrow(new IllegalStateException("match failed"));

        assertThatThrownBy(() -> service.mapNews(new AutoMapNewsCommand(10L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("match failed");
        verifyNoInteractions(mappingService);
    }

    @Test
    void propagatesSaveFailureAndStopsFollowingTerms() {
        prepare(article(10L, "GDP CPI", null), List.of(
                term(1L, "GDP", "gdp", List.of()),
                term(2L, "CPI", "cpi", List.of())
        ));
        when(mappingService.save(any())).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.mapNews(new AutoMapNewsCommand(10L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        InOrder order = inOrder(mappingService);
        order.verify(mappingService).save(any());
        order.verifyNoMoreInteractions();
    }

    private void prepare(NewsArticle article, List<EconomicTerm> terms) {
        when(articleRepository.findById(article.getId())).thenReturn(Optional.of(article));
        when(termRepository.findAllByStatusOrderByIdAsc(TermStatus.ACTIVE)).thenReturn(terms);
    }

    private static EconomicTerm term(
            Long id,
            String name,
            String normalizedName,
            List<EconomicTermAlias> aliases
    ) {
        EconomicTerm term = new EconomicTerm(name, normalizedName, "definition", aliases);
        ReflectionTestUtils.setField(term, "id", id);
        return term;
    }

    private static EconomicTermAlias alias(String value, String normalizedValue) {
        return new EconomicTermAlias(value, normalizedValue);
    }

    private static NewsArticle article(Long id, String title, String summary) {
        NewsArticle article = new NewsArticle(
                title,
                summary,
                "source",
                "https://example.com/news/" + id,
                new byte[32],
                LocalDateTime.parse("2026-07-15T00:00:00"),
                LocalDateTime.parse("2026-07-15T00:00:00")
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private static TermNewsMappingResult mappingResult(
            Long termId,
            MatchType matchType,
            MappingSaveStatus status
    ) {
        return new TermNewsMappingResult(
                100L,
                termId,
                10L,
                matchType,
                BigDecimal.ONE,
                status,
                Instant.parse("2026-07-15T00:00:00Z")
        );
    }
}
