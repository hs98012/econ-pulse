package com.econpulse.news.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.news.application.NewsUrl;
import com.econpulse.news.application.NewsUrlHasher;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class NewsArticleRepositoryTest extends AbstractIntegrationTest {

    private final NewsArticleRepository newsArticleRepository;
    private final NewsUrlHasher newsUrlHasher = new NewsUrlHasher();

    @Autowired
    NewsArticleRepositoryTest(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    @BeforeEach
    void setUp() {
        newsArticleRepository.deleteAll();
    }

    @Test
    void findsArticleBySourceUrlHash() {
        NewsArticle savedArticle = newsArticleRepository.saveAndFlush(article("https://example.com/news/1"));

        NewsArticle foundArticle = newsArticleRepository.findBySourceUrlHash(savedArticle.getSourceUrlHash()).orElseThrow();

        assertThat(foundArticle.getId()).isEqualTo(savedArticle.getId());
    }

    @Test
    void sourceUrlHashIsUnique() {
        newsArticleRepository.saveAndFlush(article("https://example.com/news/1"));

        assertThatThrownBy(() -> newsArticleRepository.saveAndFlush(article("https://example.com/news/1#fragment")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesNewArticle() {
        NewsArticle savedArticle = newsArticleRepository.saveAndFlush(article("https://example.com/news/1"));

        assertThat(savedArticle.getId()).isNotNull();
        assertThat(savedArticle.getCollectedAt()).isEqualTo(LocalDateTime.parse("2026-07-14T00:00:00"));
    }

    @Test
    void updatesExistingArticle() {
        NewsArticle savedArticle = newsArticleRepository.saveAndFlush(article("https://example.com/news/1"));

        boolean changed = savedArticle.updateFrom(
                "수정 제목",
                "수정 요약",
                "Updated News",
                savedArticle.getSourceUrl(),
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z")
        );
        newsArticleRepository.saveAndFlush(savedArticle);

        assertThat(changed).isTrue();
        NewsArticle foundArticle = newsArticleRepository.findById(savedArticle.getId()).orElseThrow();
        assertThat(foundArticle.getTitle()).isEqualTo("수정 제목");
        assertThat(foundArticle.getCollectedAt()).isEqualTo(LocalDateTime.parse("2026-07-15T00:00:00"));
    }

    private NewsArticle article(String sourceUrl) {
        NewsUrl newsUrl = newsUrlHasher.hash(sourceUrl);
        return NewsArticle.create(
                "제목",
                "요약",
                "Example News",
                newsUrl.normalizedUrl(),
                newsUrl.hash(),
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-14T00:00:00Z")
        );
    }
}
