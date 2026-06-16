package com.econpulse.term.infrastructure;

import com.econpulse.term.domain.EconomicTerm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {

    @Override
    @EntityGraph(attributePaths = "newsMappings")
    List<EconomicTerm> findAll();

    Optional<EconomicTerm> findByName(String name);

    Optional<EconomicTerm> findByNormalizedName(String normalizedName);

    boolean existsByName(String name);

    boolean existsByNormalizedName(String normalizedName);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);
}
