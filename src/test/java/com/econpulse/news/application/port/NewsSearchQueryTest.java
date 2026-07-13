package com.econpulse.news.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NewsSearchQueryTest {

    @Test
    void rejectsEmptyQuery() {
        assertThatThrownBy(() -> new NewsSearchQuery("", 0, 10, NewsSort.RECENCY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> new NewsSearchQuery("   ", 0, 10, NewsSort.RECENCY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidSize() {
        assertThatThrownBy(() -> new NewsSearchQuery("금리", 0, 0, NewsSort.RECENCY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new NewsSearchQuery("금리", 0, NewsSearchQuery.MAX_SIZE + 1, NewsSort.RECENCY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsValidQueryWithNormalizedText() {
        NewsSearchQuery query = new NewsSearchQuery(" ＧＤＰ　 성장 ", 1, 20, NewsSort.RELEVANCE);

        assertThat(query.query()).isEqualTo("gdp 성장");
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sort()).isEqualTo(NewsSort.RELEVANCE);
    }
}
