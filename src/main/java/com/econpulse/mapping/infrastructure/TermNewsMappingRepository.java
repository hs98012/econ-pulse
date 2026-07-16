package com.econpulse.mapping.infrastructure;

import com.econpulse.mapping.domain.TermNewsMapping;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermNewsMappingRepository extends JpaRepository<TermNewsMapping, Long> {

    Optional<TermNewsMapping> findByEconomicTermIdAndNewsArticleId(Long economicTermId, Long newsArticleId);

    long countByEconomicTermId(Long economicTermId);

    @Query(
            value = """
                    select mapping
                    from TermNewsMapping mapping
                    join fetch mapping.newsArticle article
                    where mapping.economicTerm.id = :economicTermId
                    order by article.publishedAt desc, article.id desc
                    """,
            countQuery = """
                    select count(mapping)
                    from TermNewsMapping mapping
                    where mapping.economicTerm.id = :economicTermId
                    """
    )
    Page<TermNewsMapping> findRelatedNewsByEconomicTermId(
            @Param("economicTermId") Long economicTermId,
            Pageable pageable
    );
}
