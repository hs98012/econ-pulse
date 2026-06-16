package com.econpulse.term.infrastructure;

import com.econpulse.term.domain.EconomicTerm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {

    Optional<EconomicTerm> findByName(String name);

    Optional<EconomicTerm> findByNormalizedName(String normalizedName);

    boolean existsByName(String name);

    boolean existsByNormalizedName(String normalizedName);
}
