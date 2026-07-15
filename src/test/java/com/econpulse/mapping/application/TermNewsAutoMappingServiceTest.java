package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMatcher;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermNewsAutoMappingServiceTest {

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
    void createsExactNameMappingForSingleNews() {
        EconomicTerm term = term(1L, "GDP", "gdp", List.of());
        NewsArticle article = article(2L, "GDP 성장률", null);
        prepare(List.of(article), List.of(term));
        when(mappingService.save(any())).thenReturn(mappingResult(1L, 2L, MatchType.EXACT_NAME,
                MappingSaveStatus.CREATED));

        TermNewsAutoMappingResult result = service.map(TermNewsAutoMappingCommand.single(2L));

        assertThat(result).isEqualTo(new TermNewsAutoMappingResult(1, 1, 1, 1, 1, 1, 0, 0, 0));
        ArgumentCaptor<TermNewsMappingCommand> captor = ArgumentCaptor.forClass(TermNewsMappingCommand.class);
        verify(mappingService).save(captor.capture());
        assertThat(captor.getValue().economicTermId()).isEqualTo(1L);
        assertThat(captor.getValue().newsArticleId()).isEqualTo(2L);
        assertThat(captor.getValue().matchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(captor.getValue().confidenceScore()).isEqualByComparingTo("1.0000");
    }

    @Test
    void createsAliasCommandAndDoesNotPersistExplanationFields() {
        EconomicTerm term = term(1L, "소비자물가지수", "소비자물가지수", List.of(
                new EconomicTermAlias("CPI", "cpi")
        ));
        prepare(List.of(article(2L, "CPI 상승", null)), List.of(term));
        when(mappingService.save(any())).thenReturn(mappingResult(1L, 2L, MatchType.ALIAS,
                MappingSaveStatus.CREATED));

        service.map(TermNewsAutoMappingCommand.single(2L));

        ArgumentCaptor<TermNewsMappingCommand> captor = ArgumentCaptor.forClass(TermNewsMappingCommand.class);
        verify(mappingService).save(captor.capture());
        assertThat(captor.getValue().matchType()).isEqualTo(MatchType.ALIAS);
        assertThat(captor.getValue().confidenceScore()).isEqualByComparingTo("0.8000");
    }

    @Test
    void doesNotCallMappingServiceForUnmatchedPair() {
        prepare(List.of(article(2L, "환율 전망", null)), List.of(term(1L, "GDP", "gdp", List.of())));

        TermNewsAutoMappingResult result = service.map(TermNewsAutoMappingCommand.single(2L));

        assertThat(result.evaluatedPairCount()).isEqualTo(1);
        assertThat(result.matchedCandidateCount()).isZero();
        assertThat(result.unmatchedPairCount()).isEqualTo(1);
        verify(mappingService, never()).save(any());
    }

    @Test
    void evaluatesOnlyActiveTermsReturnedByActiveQueryAndSavesOnlyMatches() {
        EconomicTerm gdp = term(1L, "GDP", "gdp", List.of());
        EconomicTerm cpi = term(2L, "CPI", "cpi", List.of());
        prepare(List.of(article(3L, "GDP 성장", null)), List.of(cpi, gdp));
        when(mappingService.save(any())).thenReturn(mappingResult(1L, 3L, MatchType.EXACT_NAME,
                MappingSaveStatus.CREATED));

        TermNewsAutoMappingResult result = service.map(TermNewsAutoMappingCommand.single(3L));

        assertThat(result.activeTermCount()).isEqualTo(2);
        assertThat(result.evaluatedPairCount()).isEqualTo(2);
        assertThat(result.matchedCandidateCount()).isEqualTo(1);
        assertThat(result.unmatchedPairCount()).isEqualTo(1);
        verify(termRepository).findAllByStatusOrderByIdAsc(TermStatus.ACTIVE);
    }

    @Test
    void processesNewsAndTermsInIdOrderRegardlessOfInputAndRepositoryOrder() {
        EconomicTerm firstTerm = term(1L, "GDP", "gdp", List.of());
        EconomicTerm secondTerm = term(2L, "CPI", "cpi", List.of());
        NewsArticle firstNews = article(10L, "GDP CPI", null);
        NewsArticle secondNews = article(20L, "GDP CPI", null);
        when(articleRepository.findAllByIdInOrderByIdAsc(List.of(10L, 20L)))
                .thenReturn(List.of(secondNews, firstNews));
        when(termRepository.findAllByStatusOrderByIdAsc(TermStatus.ACTIVE))
                .thenReturn(List.of(secondTerm, firstTerm));
        when(mappingService.save(any())).thenAnswer(invocation -> {
            TermNewsMappingCommand command = invocation.getArgument(0);
            return mappingResult(command.economicTermId(), command.newsArticleId(), command.matchType(),
                    MappingSaveStatus.SKIPPED);
        });

        TermNewsAutoMappingResult result = service.map(new TermNewsAutoMappingCommand(List.of(20L, 10L, 20L)));

        ArgumentCaptor<TermNewsMappingCommand> captor = ArgumentCaptor.forClass(TermNewsMappingCommand.class);
        verify(mappingService, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(command -> command.newsArticleId() + ":" + command.economicTermId())
                .containsExactly("10:1", "10:2", "20:1", "20:2");
        assertThat(result.requestedNewsCount()).isEqualTo(2);
        assertThat(result.processedNewsCount()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(4);
    }

    @Test
    void aggregatesCreatedUpdatedAndSkippedStatuses() {
        List<EconomicTerm> terms = List.of(
                term(1L, "GDP", "gdp", List.of()),
                term(2L, "CPI", "cpi", List.of()),
                term(3L, "PPI", "ppi", List.of()),
                term(4L, "환율", "환율", List.of())
        );
        prepare(List.of(article(10L, "GDP CPI PPI", null)), terms);
        when(mappingService.save(any()))
                .thenReturn(
                        mappingResult(1L, 10L, MatchType.EXACT_NAME, MappingSaveStatus.CREATED),
                        mappingResult(2L, 10L, MatchType.EXACT_NAME, MappingSaveStatus.UPDATED),
                        mappingResult(3L, 10L, MatchType.EXACT_NAME, MappingSaveStatus.SKIPPED)
                );

        TermNewsAutoMappingResult result = service.map(TermNewsAutoMappingCommand.single(10L));

        assertThat(result.evaluatedPairCount()).isEqualTo(4);
        assertThat(result.matchedCandidateCount()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.unmatchedPairCount()).isEqualTo(1);
        assertThat(result.evaluatedPairCount())
                .isEqualTo(result.matchedCandidateCount() + result.unmatchedPairCount());
        assertThat(result.matchedCandidateCount())
                .isEqualTo(result.created() + result.updated() + result.skipped());
    }

    @Test
    void failsWhenAnyRequestedNewsIsMissing() {
        when(articleRepository.findAllByIdInOrderByIdAsc(List.of(10L, 20L)))
                .thenReturn(List.of(article(10L, "GDP", null)));

        assertThatThrownBy(() -> service.map(new TermNewsAutoMappingCommand(List.of(20L, 10L))))
                .isInstanceOf(NewsNotFoundException.class);
        verify(termRepository, never()).findAllByStatusOrderByIdAsc(any());
        verify(mappingService, never()).save(any());
    }

    @Test
    void propagatesMappingFailureWithoutReturningPartialResult() {
        prepare(List.of(article(2L, "GDP", null)), List.of(term(1L, "GDP", "gdp", List.of())));
        when(mappingService.save(any())).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.map(TermNewsAutoMappingCommand.single(2L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> service.map(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void prepare(List<NewsArticle> articles, List<EconomicTerm> terms) {
        List<Long> ids = articles.stream().map(NewsArticle::getId).sorted().toList();
        when(articleRepository.findAllByIdInOrderByIdAsc(ids)).thenReturn(articles);
        when(termRepository.findAllByStatusOrderByIdAsc(TermStatus.ACTIVE)).thenReturn(terms);
    }

    private EconomicTerm term(Long id, String name, String normalizedName, List<EconomicTermAlias> aliases) {
        EconomicTerm term = new EconomicTerm(name, normalizedName, "definition", aliases);
        ReflectionTestUtils.setField(term, "id", id);
        return term;
    }

    private NewsArticle article(Long id, String title, String summary) {
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

    private TermNewsMappingResult mappingResult(
            Long termId,
            Long articleId,
            MatchType matchType,
            MappingSaveStatus status
    ) {
        return new TermNewsMappingResult(
                100L,
                termId,
                articleId,
                matchType,
                java.math.BigDecimal.ONE,
                status,
                Instant.parse("2026-07-15T00:00:00Z")
        );
    }
}
