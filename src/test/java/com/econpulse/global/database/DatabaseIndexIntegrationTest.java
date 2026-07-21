package com.econpulse.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseIndexIntegrationTest extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DatabaseIndexIntegrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void queryIndexesAndUniqueBusinessConstraintsMatchTheReviewedSchema() {
        assertIndex("economic_terms", "PRIMARY", true, "id");
        assertIndex("economic_terms", "uk_economic_terms_normalized_name", true, "normalized_name");
        assertIndex("economic_terms", "idx_economic_terms_status_name_id", false, "status", "name", "id");

        assertIndex("economic_term_aliases", "PRIMARY", true, "id");
        assertIndex("economic_term_aliases", "uk_economic_term_aliases_normalized_alias", true,
                "normalized_alias");
        assertIndex("economic_term_aliases", "idx_economic_term_aliases_term_id", false, "economic_term_id");
        assertIndexMissing("economic_term_aliases", "idx_economic_term_aliases_normalized_alias");

        assertIndex("news_articles", "PRIMARY", true, "id");
        assertIndex("news_articles", "uk_news_articles_source_url_hash", true, "source_url_hash");
        assertIndex("news_articles", "idx_news_articles_published_at", false, "published_at");

        assertIndex("term_news_mappings", "PRIMARY", true, "id");
        assertIndex("term_news_mappings", "uk_term_news_mappings_term_article", true,
                "economic_term_id", "news_article_id");
        assertIndex("term_news_mappings", "idx_term_news_mappings_article", false, "news_article_id");
        assertIndexMissing("term_news_mappings", "idx_term_news_mappings_term_article");
    }

    private void assertIndex(String table, String index, boolean unique, String... columns) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select index_name, non_unique, column_name, seq_in_index
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and index_name = ?
                order by seq_in_index
                """, table, index);

        assertThat(rows).isNotEmpty();
        assertThat(rows).extracting(row -> row.get("COLUMN_NAME")).containsExactly((Object[]) columns);
        assertThat(rows).allMatch(row -> ((Number) row.get("NON_UNIQUE")).intValue() == (unique ? 0 : 1));
    }

    private void assertIndexMissing(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and index_name = ?
                """, Integer.class, table, index);
        assertThat(count).isZero();
    }
}
