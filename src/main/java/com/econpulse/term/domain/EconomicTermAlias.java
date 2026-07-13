package com.econpulse.term.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "economic_term_aliases",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_economic_term_aliases_normalized_alias", columnNames = "normalized_alias")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EconomicTermAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "economic_term_id", nullable = false)
    private EconomicTerm economicTerm;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 100)
    private String normalizedAlias;

    public EconomicTermAlias(String alias, String normalizedAlias) {
        this.alias = alias;
        this.normalizedAlias = normalizedAlias;
    }

    void assignTerm(EconomicTerm economicTerm) {
        this.economicTerm = economicTerm;
    }
}
