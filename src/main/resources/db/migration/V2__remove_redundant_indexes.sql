ALTER TABLE economic_term_aliases
    DROP INDEX idx_economic_term_aliases_normalized_alias;

ALTER TABLE term_news_mappings
    DROP INDEX idx_term_news_mappings_term_article;
