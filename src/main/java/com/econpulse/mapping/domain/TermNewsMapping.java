package com.econpulse.mapping.domain;

import com.econpulse.news.domain.NewsArticle;
import com.econpulse.term.domain.EconomicTerm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "term_news_mappings",
        indexes = {
                @Index(name = "idx_term_news_mappings_article", columnList = "news_article_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_term_news_mappings_term_article",
                        columnNames = {"economic_term_id", "news_article_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermNewsMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "economic_term_id", nullable = false)
    private EconomicTerm economicTerm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_article_id", nullable = false)
    private NewsArticle newsArticle;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 30)
    private MatchType matchType;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "matched_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime matchedAt;

    public TermNewsMapping(
            EconomicTerm economicTerm,
            NewsArticle newsArticle,
            MatchType matchType,
            BigDecimal confidenceScore,
            LocalDateTime matchedAt
    ) {
        this.economicTerm = requireNonNull(economicTerm, "economicTerm");
        this.newsArticle = requireNonNull(newsArticle, "newsArticle");
        this.matchType = requireNonNull(matchType, "matchType");
        this.confidenceScore = requireNonNull(confidenceScore, "confidenceScore");
        this.matchedAt = requireNonNull(matchedAt, "matchedAt");
    }

    public boolean hasSameEvidence(MatchType type, BigDecimal score) {
        return matchType == type && confidenceScore.compareTo(requireNonNull(score, "score")) == 0;
    }

    public boolean updateEvidenceIfStronger(MatchType type, BigDecimal score, LocalDateTime newMatchedAt) {
        requireNonNull(type, "type");
        requireNonNull(score, "score");
        requireNonNull(newMatchedAt, "matchedAt");

        boolean strongerType = type.hasHigherPriorityThan(matchType);
        boolean strongerScore = type == matchType && score.compareTo(confidenceScore) > 0;
        if (!strongerType && !strongerScore) {
            return false;
        }

        this.matchType = type;
        this.confidenceScore = score;
        this.matchedAt = newMatchedAt;
        return true;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
