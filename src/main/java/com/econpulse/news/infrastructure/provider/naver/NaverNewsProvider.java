package com.econpulse.news.infrastructure.provider.naver;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsProviderMetrics;
import com.econpulse.news.application.port.NewsSearchQuery;
import com.econpulse.news.application.port.NewsSearchResult;
import com.econpulse.news.application.port.NewsSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class NaverNewsProvider implements NewsProvider {

    static final int MAX_START = 1000;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private final NaverNewsProperties properties;
    private final ObjectMapper objectMapper;
    private final NaverNewsResponseMapper responseMapper;
    private final RestClient restClient;
    private final NewsProviderMetrics metrics;

    public NaverNewsProvider(NaverNewsProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, NewsProviderMetrics.NO_OP);
    }

    public NaverNewsProvider(
            NaverNewsProperties properties,
            ObjectMapper objectMapper,
            NewsProviderMetrics metrics
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.responseMapper = new NaverNewsResponseMapper();
        this.metrics = metrics;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public NewsSearchResult search(NewsSearchQuery query) {
        int start = calculateStart(query);
        NewsProviderMetrics.Request request = metrics.startRequest();
        try {
            byte[] body = restClient.get()
                    .uri(
                            "/v1/search/news.json?query={query}&display={display}&start={start}&sort={sort}",
                            query.query(),
                            query.size(),
                            start,
                            toExternalSort(query.sort())
                    )
                    .header("X-Naver-Client-Id", properties.clientId())
                    .header("X-Naver-Client-Secret", properties.clientSecret())
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > MAX_RESPONSE_BYTES) {
                throw invalidResponse();
            }
            NaverNewsSearchResponse response = objectMapper.readValue(body, NaverNewsSearchResponse.class);
            NewsSearchResult result = responseMapper.map(response, query, start);
            request.success();
            return result;
        } catch (RestClientResponseException exception) {
            throw recordFailure(request, statusException(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw recordFailure(request, new NewsProviderException(
                        NewsProviderErrorType.TIMEOUT,
                        "News provider timed out."
                ));
            }
            throw recordFailure(request, new NewsProviderException(
                    NewsProviderErrorType.TEMPORARY_FAILURE,
                    "News provider connection failed."
            ));
        } catch (NewsProviderException exception) {
            throw recordFailure(request, exception);
        } catch (RestClientException exception) {
            if (hasTimeoutCause(exception) || hasNetworkIoCause(exception)) {
                throw recordFailure(request, new NewsProviderException(
                        NewsProviderErrorType.TIMEOUT,
                        "News provider timed out."
                ));
            }
            throw recordFailure(request, invalidResponse());
        } catch (IOException | IllegalArgumentException exception) {
            throw recordFailure(request, invalidResponse());
        }
    }

    private NewsProviderException recordFailure(
            NewsProviderMetrics.Request request,
            NewsProviderException exception
    ) {
        request.failure(exception.getErrorType());
        return exception;
    }

    private int calculateStart(NewsSearchQuery query) {
        long start = (long) query.page() * query.size() + 1L;
        if (start > MAX_START) {
            throw new NewsProviderException(
                    NewsProviderErrorType.INVALID_REQUEST,
                    "Requested news page exceeds provider limits."
            );
        }
        return (int) start;
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

    private boolean hasNetworkIoCause(Throwable exception) {
        Throwable current = exception.getCause();
        while (current != null) {
            if (current instanceof IOException) {
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
}
