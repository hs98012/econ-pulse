package com.econpulse.popular.infrastructure;

import com.econpulse.popular.domain.PopularTermSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopularTermSnapshotRepository extends JpaRepository<PopularTermSnapshot, Long> {
}
