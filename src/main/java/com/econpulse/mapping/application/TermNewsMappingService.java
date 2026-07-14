package com.econpulse.mapping.application;

import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermNewsMappingService {

    private final EconomicTermRepository economicTermRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final TermNewsMappingRepository mappingRepository;
    private final Clock clock;

    public TermNewsMappingService(
            EconomicTermRepository economicTermRepository,
            NewsArticleRepository newsArticleRepository,
            TermNewsMappingRepository mappingRepository,
            Clock clock
    ) {
        this.economicTermRepository = economicTermRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.mappingRepository = mappingRepository;
        this.clock = clock;
    }

    @Transactional
    public TermNewsMappingResult save(TermNewsMappingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        EconomicTerm term = economicTermRepository.findById(command.economicTermId())
                .orElseThrow(TermNotFoundException::new);
        if (term.getStatus() != TermStatus.ACTIVE) {
            throw new InactiveTermException();
        }
        NewsArticle article = newsArticleRepository.findById(command.newsArticleId())
                .orElseThrow(NewsNotFoundException::new);

        return mappingRepository.findByEconomicTermIdAndNewsArticleId(term.getId(), article.getId())
                .map(mapping -> updateOrSkip(mapping, command))
                .orElseGet(() -> create(term, article, command));
    }

    private TermNewsMappingResult create(
            EconomicTerm term,
            NewsArticle article,
            TermNewsMappingCommand command
    ) {
        TermNewsMapping mapping = new TermNewsMapping(
                term,
                article,
                command.matchType(),
                command.confidenceScore(),
                nowUtc()
        );
        try {
            return TermNewsMappingResult.from(
                    mappingRepository.saveAndFlush(mapping),
                    MappingSaveStatus.CREATED
            );
        } catch (DataIntegrityViolationException exception) {
            throw new TermNewsMappingConflictException();
        }
    }

    private TermNewsMappingResult updateOrSkip(
            TermNewsMapping mapping,
            TermNewsMappingCommand command
    ) {
        if (mapping.hasSameEvidence(command.matchType(), command.confidenceScore())) {
            return TermNewsMappingResult.from(mapping, MappingSaveStatus.SKIPPED);
        }
        if (!mapping.updateEvidenceIfStronger(command.matchType(), command.confidenceScore(), nowUtc())) {
            return TermNewsMappingResult.from(mapping, MappingSaveStatus.SKIPPED);
        }
        return TermNewsMappingResult.from(mappingRepository.saveAndFlush(mapping), MappingSaveStatus.UPDATED);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
