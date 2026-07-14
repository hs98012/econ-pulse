package com.econpulse.news.infrastructure.provider;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalFakeNewsProviderConfig {

    @Bean
    @ConditionalOnMissingBean(NewsProvider.class)
    public NewsProvider localFakeNewsProvider() {
        return new FakeNewsProvider(List.of(
                article("local-1", "한국은행 기준금리 동결", "기준금리 결정 배경"),
                article("local-2", "기준금리 전망", "시장 기준금리 전망")
        ));
    }

    private NewsProviderArticle article(String id, String title, String summary) {
        return new NewsProviderArticle(
                id,
                title,
                summary,
                "Local Example News",
                "https://example.com/local-news/" + id,
                Instant.parse("2026-07-14T00:00:00Z")
        );
    }
}
