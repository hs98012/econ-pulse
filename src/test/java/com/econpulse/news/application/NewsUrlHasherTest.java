package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NewsUrlHasherTest {

    private final NewsUrlHasher newsUrlHasher = new NewsUrlHasher();

    @Test
    void createsStableSha256HashForSameUrl() {
        NewsUrl first = newsUrlHasher.hash(" HTTPS://Example.com:443/news/1?utm=keep#section ");
        NewsUrl second = newsUrlHasher.hash("https://example.com/news/1?utm=keep");

        assertThat(first.normalizedUrl()).isEqualTo("https://example.com/news/1?utm=keep");
        assertThat(first.hash()).containsExactly(second.hash());
    }

    @Test
    void hashIsExactlyThirtyTwoBytes() {
        NewsUrl newsUrl = newsUrlHasher.hash("https://example.com/news/1");

        assertThat(newsUrl.hash()).hasSize(32);
    }

    @Test
    void lowercasesSchemeAndHost() {
        NewsUrl newsUrl = newsUrlHasher.hash("HTTP://Example.COM/news/1");

        assertThat(newsUrl.normalizedUrl()).isEqualTo("http://example.com/news/1");
    }

    @Test
    void removesFragment() {
        NewsUrl newsUrl = newsUrlHasher.hash("https://example.com/news/1#comments");

        assertThat(newsUrl.normalizedUrl()).isEqualTo("https://example.com/news/1");
    }

    @Test
    void preservesQueryParameters() {
        NewsUrl first = newsUrlHasher.hash("https://example.com/news/1?a=1");
        NewsUrl second = newsUrlHasher.hash("https://example.com/news/1?a=2");

        assertThat(first.normalizedUrl()).endsWith("?a=1");
        assertThat(second.normalizedUrl()).endsWith("?a=2");
        assertThat(first.hash()).isNotEqualTo(second.hash());
    }

    @Test
    void rejectsInvalidUrl() {
        assertThatThrownBy(() -> newsUrlHasher.hash("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
