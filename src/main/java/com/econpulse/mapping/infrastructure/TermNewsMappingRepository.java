package com.econpulse.mapping.infrastructure;

import com.econpulse.mapping.domain.TermNewsMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermNewsMappingRepository extends JpaRepository<TermNewsMapping, Long> {

    Optional<TermNewsMapping> findByEconomicTermIdAndNewsArticleId(Long economicTermId, Long newsArticleId);

    long countByEconomicTermId(Long economicTermId);
}
