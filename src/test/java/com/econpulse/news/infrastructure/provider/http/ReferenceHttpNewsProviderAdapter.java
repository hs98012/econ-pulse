package com.econpulse.news.infrastructure.provider.http;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderArticle;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.infrastructure.provider.ExternalNewsTextSanitizer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class ReferenceHttpNewsProviderAdapter implements NewsProvider {

    private final RestClient restClient;

    ReferenceHttpNewsProviderAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public NewsSearchResult search(NewsSearchQuery query) {
        try {
            ExternalResponse response = restClient.get()
                    .uri(
                            "/search?query={query}&start={start}&display={size}&sort={sort}",
                            query.query(),
                            query.page() * query.size() + 1,
                            query.size(),
                            toExternalSort(query.sort())
                    )
                    .retrieve()
                    .body(ExternalResponse.class);
            return toResult(response, query);
        } catch (RestClientResponseException exception) {
            throw statusException(exception.getStatusCode());
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new NewsProviderException(NewsProviderErrorType.TIMEOUT, "News provider timed out.");
            }
            throw new NewsProviderException(
                    NewsProviderErrorType.TEMPORARY_FAILURE,
                    "News provider connection failed."
            );
        } catch (NewsProviderException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private NewsSearchResult toResult(ExternalResponse response, NewsSearchQuery query) {
        if (response == null || response.items() == null) {
            throw invalidResponse();
        }
        List<NewsProviderArticle> articles = response.items().stream()
                .map(this::toArticle)
                .toList();
        OptionalLong total = response.total() == null
                ? OptionalLong.empty()
                : OptionalLong.of(response.total());
        return new NewsSearchResult(articles, query.page(), query.size(), total, response.hasNext());
    }

    private NewsProviderArticle toArticle(ExternalArticle article) {
        if (article == null) {
            throw invalidResponse();
        }
        String title = ExternalNewsTextSanitizer.sanitize(article.title());
        String summary = ExternalNewsTextSanitizer.sanitize(article.summary());
        String sourceUrl = requireText(article.sourceUrl());
        String sourceName = ExternalNewsTextSanitizer.sanitize(article.sourceName());
        if (sourceName == null || sourceName.isBlank()) {
            sourceName = sourceHost(sourceUrl);
        }
        try {
            return new NewsProviderArticle(
                    requireText(article.id()),
                    requireText(title),
                    summary,
                    sourceName,
                    sourceUrl,
                    Instant.parse(requireText(article.publishedAt()))
            );
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private String sourceHost(String sourceUrl) {
        try {
            String host = URI.create(sourceUrl).getHost();
            return requireText(host);
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private String toExternalSort(NewsSort sort) {
        return sort == NewsSort.RECENCY ? "date" : "sim";
    }

    private NewsProviderException statusException(HttpStatusCode statusCode) {
        int status = statusCode.value();
        if (status == 401 || status == 403) {
            return new NewsProviderException(
                    NewsProviderErrorType.AUTHENTICATION_FAILED,
                    "News provider authentication failed."
            );
        }
        if (status == 429) {
            return new NewsProviderException(NewsProviderErrorType.RATE_LIMITED, "News provider rate limit reached.");
        }
        if (status >= 500 && status <= 504) {
            return new NewsProviderException(
                    NewsProviderErrorType.TEMPORARY_FAILURE,
                    "News provider is temporarily unavailable."
            );
        }
        return invalidResponse();
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private NewsProviderException invalidResponse() {
        return new NewsProviderException(
                NewsProviderErrorType.INVALID_RESPONSE,
                "News provider returned an invalid response."
        );
    }

    private record ExternalResponse(
            List<ExternalArticle> items,
            Long total,
            boolean hasNext
    ) {
    }

    private record ExternalArticle(
            String id,
            String title,
            String summary,
            String sourceName,
            String sourceUrl,
            String publishedAt
    ) {
    }
}
