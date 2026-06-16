package com.econpulse.mapping.infrastructure;

import com.econpulse.mapping.domain.TermNewsMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermNewsMappingRepository extends JpaRepository<TermNewsMapping, Long> {

    boolean existsByEconomicTermIdAndNewsArticleId(Long economicTermId, Long newsArticleId);
}
