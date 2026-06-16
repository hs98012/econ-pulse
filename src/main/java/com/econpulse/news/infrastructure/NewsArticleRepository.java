package com.econpulse.news.infrastructure;

import com.econpulse.news.domain.NewsArticle;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Optional<NewsArticle> findBySourceUrl(String sourceUrl);

    boolean existsBySourceUrl(String sourceUrl);
}
