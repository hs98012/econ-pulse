package com.econpulse.news.domain;

import com.econpulse.global.domain.BaseTimeEntity;
import com.econpulse.mapping.domain.TermNewsMapping;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "news_articles",
        indexes = {
                @Index(name = "idx_news_articles_published_at", columnList = "published_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_news_articles_source_url_hash", columnNames = "source_url_hash")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "source_url_hash", nullable = false, length = 32, columnDefinition = "binary(32)")
    private byte[] sourceUrlHash;

    @Column(name = "published_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime publishedAt;

    @Column(name = "collected_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime collectedAt;

    @OneToMany(mappedBy = "newsArticle")
    private List<TermNewsMapping> termMappings = new ArrayList<>();

    public NewsArticle(
            String title,
            String summary,
            String sourceName,
            String sourceUrl,
            byte[] sourceUrlHash,
            LocalDateTime publishedAt,
            LocalDateTime collectedAt
    ) {
        this.title = title;
        this.summary = summary;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.sourceUrlHash = sourceUrlHash == null ? null : sourceUrlHash.clone();
        this.publishedAt = publishedAt;
        this.collectedAt = collectedAt;
    }

    public byte[] getSourceUrlHash() {
        return sourceUrlHash == null ? null : sourceUrlHash.clone();
    }

    public static NewsArticle create(
            String title,
            String summary,
            String sourceName,
            String sourceUrl,
            byte[] sourceUrlHash,
            Instant publishedAt,
            Instant collectedAt
    ) {
        return new NewsArticle(
                title,
                summary,
                sourceName,
                sourceUrl,
                sourceUrlHash,
                toLocalDateTime(publishedAt),
                toLocalDateTime(collectedAt)
        );
    }

    public boolean updateFrom(
            String title,
            String summary,
            String sourceName,
            String sourceUrl,
            Instant publishedAt,
            Instant collectedAt
    ) {
        boolean changed = false;

        if (hasText(title) && !title.equals(this.title)) {
            this.title = title;
            changed = true;
        }
        if (hasText(summary) && !summary.equals(this.summary)) {
            this.summary = summary;
            changed = true;
        }
        if (hasText(sourceName) && !sourceName.equals(this.sourceName)) {
            this.sourceName = sourceName;
            changed = true;
        }
        if (hasText(sourceUrl) && !sourceUrl.equals(this.sourceUrl)) {
            this.sourceUrl = sourceUrl;
            changed = true;
        }

        LocalDateTime nextPublishedAt = toLocalDateTime(publishedAt);
        if (!nextPublishedAt.equals(this.publishedAt)) {
            this.publishedAt = nextPublishedAt;
            changed = true;
        }

        this.collectedAt = toLocalDateTime(collectedAt);

        return changed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
