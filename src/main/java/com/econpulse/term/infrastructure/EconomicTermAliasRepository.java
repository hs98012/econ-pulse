package com.econpulse.term.infrastructure;

import com.econpulse.term.domain.EconomicTermAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicTermAliasRepository extends JpaRepository<EconomicTermAlias, Long> {

    boolean existsByNormalizedAlias(String normalizedAlias);

    boolean existsByNormalizedAliasAndEconomicTermIdNot(String normalizedAlias, Long economicTermId);
}
