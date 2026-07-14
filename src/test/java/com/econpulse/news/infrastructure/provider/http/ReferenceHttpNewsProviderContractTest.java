package com.econpulse.news.infrastructure.provider.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.econpulse.news.application.port.NewsProvider;
import com.econpulse.news.application.port.NewsSort;
import java.time.Duration;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.RecordedRequest;

class ReferenceHttpNewsProviderContractTest extends AbstractHttpNewsProviderContractTest {

    @Override
    protected NewsProvider createProvider(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return new ReferenceHttpNewsProviderAdapter(baseUrl, connectTimeout, readTimeout);
    }

    @Override
    protected void assertMappedRequest(
            RecordedRequest request,
            String query,
            int page,
            int size,
            NewsSort sort
    ) {
        HttpUrl url = request.getRequestUrl();
        assertThat(url).isNotNull();
        assertThat(url.encodedQuery()).contains("query=");
        assertThat(url.queryParameter("query")).isEqualTo(query);
        assertThat(url.queryParameter("start")).isEqualTo(String.valueOf(page * size + 1));
        assertThat(url.queryParameter("display")).isEqualTo(String.valueOf(size));
        assertThat(url.queryParameter("sort")).isEqualTo(sort == NewsSort.RECENCY ? "date" : "sim");
    }
}
