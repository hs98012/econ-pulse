package com.econpulse.news.infrastructure.provider;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.application.port.NewsTextNormalizer;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

public class FakeNewsProvider implements NewsProvider {

    private final List<NewsProviderArticle> articles;

    public FakeNewsProvider(List<NewsProviderArticle> articles) {
        this.articles = articles.stream()
                .map(this::clean)
                .toList();
    }

    @Override
    public NewsSearchResult search(NewsSearchQuery query) {
        List<NewsProviderArticle> matches = articles.stream()
                .filter(article -> matches(article, query.query()))
                .sorted(comparator(query))
                .toList();

        int fromIndex = Math.min(query.page() * query.size(), matches.size());
        int toIndex = Math.min(fromIndex + query.size(), matches.size());
        List<NewsProviderArticle> pageArticles = matches.subList(fromIndex, toIndex);

        return new NewsSearchResult(
                pageArticles,
                query.page(),
                query.size(),
                OptionalLong.of(matches.size()),
                toIndex < matches.size()
        );
    }

    private boolean matches(NewsProviderArticle article, String query) {
        return normalizedTitle(article).contains(query) || normalizedSummary(article).contains(query);
    }

    private Comparator<NewsProviderArticle> comparator(NewsSearchQuery query) {
        if (query.sort() == NewsSort.RELEVANCE) {
            return Comparator.comparingInt((NewsProviderArticle article) -> relevanceScore(article, query.query()))
                    .reversed()
                    .thenComparing(NewsProviderArticle::publishedAt, Comparator.reverseOrder())
                    .thenComparing(NewsProviderArticle::providerArticleId);
        }

        return Comparator.comparing(NewsProviderArticle::publishedAt, Comparator.reverseOrder())
                .thenComparing(NewsProviderArticle::providerArticleId);
    }

    private int relevanceScore(NewsProviderArticle article, String query) {
        return countOccurrences(normalizedTitle(article), query) * 2
                + countOccurrences(normalizedSummary(article), query);
    }

    private int countOccurrences(String value, String query) {
        int count = 0;
        int index = value.indexOf(query);
        while (index >= 0) {
            count++;
            index = value.indexOf(query, index + query.length());
        }
        return count;
    }

    private String normalizedTitle(NewsProviderArticle article) {
        return NewsTextNormalizer.normalize(article.title());
    }

    private String normalizedSummary(NewsProviderArticle article) {
        return NewsTextNormalizer.normalize(article.summary());
    }

    private NewsProviderArticle clean(NewsProviderArticle article) {
        return new NewsProviderArticle(
                article.providerArticleId(),
                cleanExternalText(article.title()),
                cleanExternalText(article.summary()),
                article.sourceName(),
                article.sourceUrl(),
                article.publishedAt()
        );
    }

    private String cleanExternalText(String value) {
        return value.replaceAll("<[^>]+>", "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }
}
