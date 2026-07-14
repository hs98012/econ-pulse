package com.econpulse.news.application;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

public class NewsIngestionService {

    private final NewsProvider newsProvider;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsUrlHasher newsUrlHasher;
    private final Clock clock;

    public NewsIngestionService(
            NewsProvider newsProvider,
            NewsArticleRepository newsArticleRepository,
            NewsUrlHasher newsUrlHasher,
            Clock clock
    ) {
        this.newsProvider = newsProvider;
        this.newsArticleRepository = newsArticleRepository;
        this.newsUrlHasher = newsUrlHasher;
        this.clock = clock;
    }

    public NewsIngestionService(
            NewsProvider newsProvider,
            NewsArticleRepository newsArticleRepository,
            Clock clock
    ) {
        this(newsProvider, newsArticleRepository, new NewsUrlHasher(), clock);
    }

    @Transactional
    public NewsIngestionResult ingest(NewsIngestionCommand command) {
        NewsSearchResult searchResult = newsProvider.search(command.toSearchQuery());
        Instant collectedAt = Instant.now(clock);
        Map<String, IngestionCandidate> candidates = new LinkedHashMap<>();
        int skipped = 0;

        for (NewsProviderArticle article : searchResult.articles()) {
            try {
                NewsUrl newsUrl = newsUrlHasher.hash(article.sourceUrl());
                if (candidates.putIfAbsent(newsUrl.hashKey(), new IngestionCandidate(article, newsUrl)) != null) {
                    skipped++;
                }
            } catch (IllegalArgumentException exception) {
                skipped++;
            }
        }

        int created = 0;
        int updated = 0;

        for (IngestionCandidate candidate : candidates.values()) {
            NewsArticle existingArticle = newsArticleRepository.findBySourceUrlHash(candidate.newsUrl().hash())
                    .orElse(null);

            if (existingArticle == null) {
                createArticle(candidate, collectedAt);
                created++;
                continue;
            }

            boolean changed = updateArticle(existingArticle, candidate, collectedAt);
            newsArticleRepository.saveAndFlush(existingArticle);
            if (changed) {
                updated++;
            } else {
                skipped++;
            }
        }

        return new NewsIngestionResult(searchResult.articles().size(), created, updated, skipped);
    }

    private void createArticle(IngestionCandidate candidate, Instant collectedAt) {
        try {
            NewsProviderArticle article = candidate.article();
            NewsArticle newsArticle = NewsArticle.create(
                    article.title(),
                    article.summary(),
                    article.sourceName(),
                    candidate.newsUrl().normalizedUrl(),
                    candidate.newsUrl().hash(),
                    article.publishedAt(),
                    collectedAt
            );
            newsArticleRepository.saveAndFlush(newsArticle);
        } catch (DataIntegrityViolationException exception) {
            throw new NewsIngestionException("News article already exists for the source URL hash.", exception);
        }
    }

    private boolean updateArticle(NewsArticle existingArticle, IngestionCandidate candidate, Instant collectedAt) {
        NewsProviderArticle article = candidate.article();
        return existingArticle.updateFrom(
                article.title(),
                article.summary(),
                article.sourceName(),
                candidate.newsUrl().normalizedUrl(),
                article.publishedAt(),
                collectedAt
        );
    }

    private record IngestionCandidate(
            NewsProviderArticle article,
            NewsUrl newsUrl
    ) {
    }
}
