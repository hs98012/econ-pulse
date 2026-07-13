CREATE TABLE economic_terms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    definition TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_economic_terms_normalized_name UNIQUE (normalized_name),
    INDEX idx_economic_terms_status_name_id (status, name, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE economic_term_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    economic_term_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    normalized_alias VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_economic_term_aliases_normalized_alias UNIQUE (normalized_alias),
    CONSTRAINT fk_economic_term_aliases_term FOREIGN KEY (economic_term_id) REFERENCES economic_terms (id),
    INDEX idx_economic_term_aliases_term_id (economic_term_id),
    INDEX idx_economic_term_aliases_normalized_alias (normalized_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_articles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    summary TEXT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_url_hash BINARY(32) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_news_articles_source_url_hash UNIQUE (source_url_hash),
    INDEX idx_news_articles_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE term_news_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    economic_term_id BIGINT NOT NULL,
    news_article_id BIGINT NOT NULL,
    match_type VARCHAR(30) NOT NULL,
    confidence_score DECIMAL(5, 4) NOT NULL,
    matched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_term_news_mappings_term_article UNIQUE (economic_term_id, news_article_id),
    CONSTRAINT fk_term_news_mappings_term FOREIGN KEY (economic_term_id) REFERENCES economic_terms (id),
    CONSTRAINT fk_term_news_mappings_article FOREIGN KEY (news_article_id) REFERENCES news_articles (id),
    INDEX idx_term_news_mappings_term_article (economic_term_id, news_article_id),
    INDEX idx_term_news_mappings_article (news_article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE popular_term_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    economic_term_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    snapshot_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_popular_term_snapshots_time_term UNIQUE (snapshot_at, economic_term_id),
    CONSTRAINT uk_popular_term_snapshots_time_rank UNIQUE (snapshot_at, rank_position),
    CONSTRAINT fk_popular_term_snapshots_term FOREIGN KEY (economic_term_id) REFERENCES economic_terms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
