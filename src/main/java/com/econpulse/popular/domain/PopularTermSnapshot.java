package com.econpulse.popular.domain;

import com.econpulse.term.domain.EconomicTerm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "popular_term_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_popular_term_snapshots_time_term",
                        columnNames = {"snapshot_at", "economic_term_id"}
                ),
                @UniqueConstraint(
                        name = "uk_popular_term_snapshots_time_rank",
                        columnNames = {"snapshot_at", "rank_position"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularTermSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "economic_term_id", nullable = false)
    private EconomicTerm economicTerm;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "snapshot_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime snapshotAt;

    public PopularTermSnapshot(
            EconomicTerm economicTerm,
            int rankPosition,
            double score,
            LocalDateTime snapshotAt
    ) {
        this.economicTerm = economicTerm;
        this.rankPosition = rankPosition;
        this.score = score;
        this.snapshotAt = snapshotAt;
    }
}
