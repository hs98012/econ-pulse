package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.econpulse.mapping.domain.MatchType;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermNewsMappingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T03:00:00Z");
    private static final LocalDateTime OLD_TIME = LocalDateTime.parse("2026-07-14T00:00:00");

    @Mock
    private EconomicTermRepository termRepository;
    @Mock
    private NewsArticleRepository articleRepository;
    @Mock
    private TermNewsMappingRepository mappingRepository;

    private TermNewsMappingService service;
    private EconomicTerm term;
    private NewsArticle article;

    @BeforeEach
    void setUp() {
        service = new TermNewsMappingService(
                termRepository,
                articleRepository,
                mappingRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        term = term();
        article = article();
    }

    @Test
    void createsNewMappingAndReturnsFinalState() {
        prepareEntities();
        when(mappingRepository.findByEconomicTermIdAndNewsArticleId(1L, 2L)).thenReturn(Optional.empty());
        when(mappingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(TermNewsMapping.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        TermNewsMappingResult result = service.save(command(MatchType.ALIAS, "0.8000"));

        assertThat(result.mappingId()).isEqualTo(3L);
        assertThat(result.economicTermId()).isEqualTo(1L);
        assertThat(result.newsArticleId()).isEqualTo(2L);
        assertThat(result.matchType()).isEqualTo(MatchType.ALIAS);
        assertThat(result.confidenceScore()).isEqualByComparingTo("0.8000");
        assertThat(result.status()).isEqualTo(MappingSaveStatus.CREATED);
        assertThat(result.matchedAt()).isEqualTo(NOW);
    }

    @Test
    void concurrentUniqueConflictIsTranslatedAndNotIgnored() {
        prepareEntities();
        when(mappingRepository.findByEconomicTermIdAndNewsArticleId(1L, 2L)).thenReturn(Optional.empty());
        when(mappingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(TermNewsMapping.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.save(command(MatchType.ALIAS, "0.8000")))
                .isInstanceOf(TermNewsMappingConflictException.class);
    }

    @Test
    void identicalEvidenceIsSkippedWithoutChangingMatchedAt() {
        TermNewsMapping mapping = existing(MatchType.ALIAS, "0.8000");
        prepareExisting(mapping);

        TermNewsMappingResult result = service.save(command(MatchType.ALIAS, "0.8"));

        assertThat(result.status()).isEqualTo(MappingSaveStatus.SKIPPED);
        assertThat(result.matchedAt()).isEqualTo(OLD_TIME.toInstant(ZoneOffset.UTC));
        verify(mappingRepository, never()).saveAndFlush(mapping);
    }

    @Test
    void aliasToExactNameIsUpdatedWithCurrentUtcTime() {
        TermNewsMapping mapping = existing(MatchType.ALIAS, "0.9000");
        prepareExisting(mapping);
        when(mappingRepository.saveAndFlush(mapping)).thenReturn(mapping);

        TermNewsMappingResult result = service.save(command(MatchType.EXACT_NAME, "0.5000"));

        assertThat(result.status()).isEqualTo(MappingSaveStatus.UPDATED);
        assertThat(result.matchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(result.confidenceScore()).isEqualByComparingTo("0.5000");
        assertThat(result.matchedAt()).isEqualTo(NOW);
    }

    @Test
    void exactNameToAliasIsSkippedAndReturnsExistingState() {
        assertSkippedWithoutMutation(MatchType.EXACT_NAME, "0.5000", MatchType.ALIAS, "1.0000");
    }

    @Test
    void sameTypeHigherScoreUpdates() {
        TermNewsMapping mapping = existing(MatchType.ALIAS, "0.5000");
        prepareExisting(mapping);
        when(mappingRepository.saveAndFlush(mapping)).thenReturn(mapping);

        TermNewsMappingResult result = service.save(command(MatchType.ALIAS, "0.7000"));

        assertThat(result.status()).isEqualTo(MappingSaveStatus.UPDATED);
        assertThat(result.confidenceScore()).isEqualByComparingTo("0.7000");
        assertThat(result.matchedAt()).isEqualTo(NOW);
    }

    @Test
    void sameTypeEqualScoreIsSkipped() {
        assertSkippedWithoutMutation(MatchType.ALIAS, "0.7000", MatchType.ALIAS, "0.7000");
    }

    @Test
    void sameTypeLowerScoreIsSkipped() {
        assertSkippedWithoutMutation(MatchType.ALIAS, "0.7000", MatchType.ALIAS, "0.6000");
    }

    @Test
    void missingTermStopsBeforeOtherRepositories() {
        when(termRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(command(MatchType.ALIAS, "0.5000")))
                .isInstanceOf(TermNotFoundException.class);
        verify(articleRepository, never()).findById(2L);
        verify(mappingRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingArticleDoesNotSave() {
        when(termRepository.findById(1L)).thenReturn(Optional.of(term));
        when(articleRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(command(MatchType.ALIAS, "0.5000")))
                .isInstanceOf(NewsNotFoundException.class);
        verify(mappingRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveTermIsRejectedBeforeArticleLookupOrSave() {
        term.deactivate();
        when(termRepository.findById(1L)).thenReturn(Optional.of(term));

        assertThatThrownBy(() -> service.save(command(MatchType.ALIAS, "0.5000")))
                .isInstanceOf(InactiveTermException.class);
        verify(articleRepository, never()).findById(2L);
        verify(mappingRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private void assertSkippedWithoutMutation(
            MatchType existingType,
            String existingScore,
            MatchType requestedType,
            String requestedScore
    ) {
        TermNewsMapping mapping = existing(existingType, existingScore);
        prepareExisting(mapping);

        TermNewsMappingResult result = service.save(command(requestedType, requestedScore));

        assertThat(result.status()).isEqualTo(MappingSaveStatus.SKIPPED);
        assertThat(result.matchType()).isEqualTo(existingType);
        assertThat(result.confidenceScore()).isEqualByComparingTo(existingScore);
        assertThat(result.matchedAt()).isEqualTo(OLD_TIME.toInstant(ZoneOffset.UTC));
        verify(mappingRepository, never()).saveAndFlush(mapping);
    }

    private void prepareEntities() {
        when(termRepository.findById(1L)).thenReturn(Optional.of(term));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article));
    }

    private void prepareExisting(TermNewsMapping mapping) {
        prepareEntities();
        when(mappingRepository.findByEconomicTermIdAndNewsArticleId(1L, 2L))
                .thenReturn(Optional.of(mapping));
    }

    private TermNewsMapping existing(MatchType type, String score) {
        return withId(new TermNewsMapping(term, article, type, new BigDecimal(score), OLD_TIME), 3L);
    }

    private TermNewsMappingCommand command(MatchType type, String score) {
        return new TermNewsMappingCommand(1L, 2L, type, new BigDecimal(score));
    }

    private EconomicTerm term() {
        EconomicTerm value = new EconomicTerm("GDP", "gdp", "definition", List.of());
        ReflectionTestUtils.setField(value, "id", 1L);
        return value;
    }

    private NewsArticle article() {
        NewsArticle value = new NewsArticle(
                "title",
                "summary",
                "source",
                "https://example.com/news/1",
                new byte[32],
                OLD_TIME,
                OLD_TIME
        );
        ReflectionTestUtils.setField(value, "id", 2L);
        return value;
    }

    private TermNewsMapping withId(TermNewsMapping mapping, Long id) {
        ReflectionTestUtils.setField(mapping, "id", id);
        return mapping;
    }
}
