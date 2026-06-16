package com.econpulse.term.domain;

import com.econpulse.global.domain.BaseTimeEntity;
import com.econpulse.mapping.domain.TermNewsMapping;
import com.econpulse.popular.domain.PopularTermSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "economic_terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_economic_terms_name", columnNames = "name"),
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases", nullable = false, columnDefinition = "json")
    private List<String> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "economicTerm")
    private List<TermNewsMapping> newsMappings = new ArrayList<>();

    @OneToMany(mappedBy = "economicTerm")
    private List<PopularTermSnapshot> popularTermSnapshots = new ArrayList<>();

    public EconomicTerm(String name, String normalizedName, String definition, List<String> aliases) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.definition = definition;
        this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
    }

    public void update(String name, String normalizedName, String definition, List<String> aliases) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.definition = definition;
        this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
    }
}
