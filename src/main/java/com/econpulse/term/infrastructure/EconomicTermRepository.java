package com.econpulse.term.infrastructure;

import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {

    @Query(
            value = """
                    select distinct term
                    from EconomicTerm term
                    where term.status = :status
                    """,
            countQuery = """
                    select count(term)
                    from EconomicTerm term
                    where term.status = :status
                    """
    )
    Page<EconomicTerm> findAllByStatusWithAliases(@Param("status") TermStatus status, Pageable pageable);

    @Query(
            value = """
                    select distinct term
                    from EconomicTerm term
                    left join term.aliases alias
                    where term.status = :status
                    and (
                        term.normalizedName like concat('%', :query, '%')
                        or alias.normalizedAlias like concat('%', :query, '%')
                    )
                    """,
            countQuery = """
                    select count(distinct term)
                    from EconomicTerm term
                    left join term.aliases alias
                    where term.status = :status
                    and (
                        term.normalizedName like concat('%', :query, '%')
                        or alias.normalizedAlias like concat('%', :query, '%')
                    )
                    """
    )
    Page<EconomicTerm> searchByNormalizedNameOrAlias(
            @Param("query") String query,
            @Param("status") TermStatus status,
            Pageable pageable
    );

    Optional<EconomicTerm> findByName(String name);

    Optional<EconomicTerm> findByNormalizedName(String normalizedName);

    Optional<EconomicTerm> findByIdAndStatus(Long id, TermStatus status);

    boolean existsByName(String name);

    boolean existsByNormalizedName(String normalizedName);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);
}
