package com.econpulse.mapping.application;

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
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermNewsAutoMappingService {

    private final EconomicTermRepository termRepository;
    private final NewsArticleRepository articleRepository;
    private final TermNewsMatcher matcher;
    private final TermNewsMappingService mappingService;
    private final TermNewsMappingMetrics metrics;

    public TermNewsAutoMappingService(
            EconomicTermRepository termRepository,
            NewsArticleRepository articleRepository,
            TermNewsMatcher matcher,
            TermNewsMappingService mappingService
    ) {
        this(termRepository, articleRepository, matcher, mappingService, TermNewsMappingMetrics.NO_OP);
    }

    @Autowired
    public TermNewsAutoMappingService(
            EconomicTermRepository termRepository,
            NewsArticleRepository articleRepository,
            TermNewsMatcher matcher,
            TermNewsMappingService mappingService,
            TermNewsMappingMetrics metrics
    ) {
        this.termRepository = termRepository;
        this.articleRepository = articleRepository;
        this.matcher = matcher;
        this.mappingService = mappingService;
        this.metrics = metrics;
    }

    public TermNewsAutoMappingResult map(TermNewsAutoMappingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        List<NewsArticle> articles = articleRepository
                .findAllByIdInOrderByIdAsc(command.newsArticleIds())
                .stream()
                .sorted(Comparator.comparing(NewsArticle::getId))
                .toList();
        if (articles.size() != command.newsArticleIds().size()) {
            throw new NewsNotFoundException();
        }

        List<TermMatchTarget> targets = termRepository
                .findAllByStatusOrderByIdAsc(TermStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(EconomicTerm::getId))
                .map(TermNewsAutoMappingService::toTarget)
                .toList();

        MappingCounts counts = new MappingCounts();
        for (NewsArticle article : articles) {
            NewsMatchContent content = toContent(article);
            for (TermMatchTarget target : targets) {
                counts.evaluated++;
                matcher.match(target, content).ifPresentOrElse(
                        candidate -> save(candidate, counts),
                        () -> counts.unmatched++
                );
            }
        }

        return new TermNewsAutoMappingResult(
                command.newsArticleIds().size(),
                articles.size(),
                targets.size(),
                counts.evaluated,
                counts.matched,
                counts.created,
                counts.updated,
                counts.skipped,
                counts.unmatched
        );
    }

    @Transactional
    public AutoMapNewsResult mapNews(AutoMapNewsCommand command) {
        TermNewsMappingMetrics.Run run = metrics.start();
        try {
            AutoMapNewsResult result = mapNewsInternal(command);
            run.success(result);
            return result;
        } catch (RuntimeException | Error exception) {
            run.failure();
            throw exception;
        }
    }

    private AutoMapNewsResult mapNewsInternal(AutoMapNewsCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        NewsArticle article = articleRepository.findById(command.newsArticleId())
                .orElseThrow(NewsNotFoundException::new);
        NewsMatchContent content = toContent(article);
        List<TermMatchTarget> targets = termRepository
                .findAllByStatusOrderByIdAsc(TermStatus.ACTIVE)
                .stream()
                .map(TermNewsAutoMappingService::toTarget)
                .toList();

        MappingCounts counts = new MappingCounts();
        for (TermMatchTarget target : targets) {
            counts.evaluated++;
            matcher.match(target, content).ifPresentOrElse(
                    candidate -> save(candidate, counts),
                    () -> counts.unmatched++
            );
        }

        return new AutoMapNewsResult(
                article.getId(),
                counts.evaluated,
                counts.matched,
                counts.created,
                counts.updated,
                counts.skipped,
                counts.unmatched
        );
    }

    private void save(TermMatchCandidate candidate, MappingCounts counts) {
        TermNewsMappingResult result = mappingService.save(new TermNewsMappingCommand(
                candidate.economicTermId(),
                candidate.newsArticleId(),
                candidate.matchType(),
                candidate.confidenceScore()
        ));
        counts.matched++;
        switch (result.status()) {
            case CREATED -> counts.created++;
            case UPDATED -> counts.updated++;
            case SKIPPED -> counts.skipped++;
        }
    }

    private static TermMatchTarget toTarget(EconomicTerm term) {
        List<String> normalizedAliases = term.getAliases().stream()
                .map(EconomicTermAlias::getNormalizedAlias)
                .toList();
        return new TermMatchTarget(term.getId(), term.getNormalizedName(), normalizedAliases);
    }

    private static NewsMatchContent toContent(NewsArticle article) {
        return new NewsMatchContent(article.getId(), article.getTitle(), article.getSummary());
    }

    private static final class MappingCounts {

        private long evaluated;
        private long matched;
        private long created;
        private long updated;
        private long skipped;
        private long unmatched;
    }
}
