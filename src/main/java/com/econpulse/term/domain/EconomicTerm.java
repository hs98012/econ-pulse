package com.econpulse.term.domain;

import com.econpulse.global.domain.BaseTimeEntity;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.popular.domain.PopularTermSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "economic_terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_economic_terms_normalized_name", columnNames = "normalized_name")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EconomicTerm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "definition", nullable = false, columnDefinition = "text")
    private String definition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TermStatus status = TermStatus.ACTIVE;

    @OneToMany(mappedBy = "economicTerm", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<EconomicTermAlias> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "economicTerm", fetch = FetchType.LAZY)
    private List<TermNewsMapping> newsMappings = new ArrayList<>();

    @OneToMany(mappedBy = "economicTerm", fetch = FetchType.LAZY)
    private List<PopularTermSnapshot> popularTermSnapshots = new ArrayList<>();

    public EconomicTerm(String name, String normalizedName, String definition, List<EconomicTermAlias> aliases) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.definition = definition;
        replaceAliases(aliases);
    }

    public void update(String name, String normalizedName, String definition, List<EconomicTermAlias> aliases) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.definition = definition;
        replaceAliases(aliases);
    }

    public void deactivate() {
        this.status = TermStatus.INACTIVE;
    }

    public List<String> getAliasValues() {
        return aliases.stream()
                .map(EconomicTermAlias::getAlias)
                .toList();
    }

    private void replaceAliases(List<EconomicTermAlias> aliases) {
        this.aliases.clear();
        if (aliases != null) {
            aliases.forEach(this::addAlias);
        }
    }

    private void addAlias(EconomicTermAlias alias) {
        alias.assignTerm(this);
        this.aliases.add(alias);
    }
}
