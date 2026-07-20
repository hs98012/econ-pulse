package com.econpulse.news.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.news.application.NewsIngestionCommand;
import com.econpulse.news.application.NewsIngestionResult;
import com.econpulse.news.application.NewsIngestionService;
import com.econpulse.news.application.port.NewsProviderErrorType;
import com.econpulse.news.application.port.NewsProviderException;
import com.econpulse.news.application.port.NewsSort;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.news.infrastructure.provider.FakeNewsProvider;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = NewsSyncController.class, properties = "econpulse.internal.news-sync.enabled=true")
@Import(NewsProviderExceptionHandler.class)
@ExtendWith(OutputCaptureExtension.class)
class NewsSyncControllerTest {

    private static final String PATH = "/internal/api/v1/news/sync";
    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private NewsIngestionService newsIngestionService;

    @Autowired
    NewsSyncControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void validRequestInvokesIngestionServiceAndReturnsCounts() throws Exception {
        NewsIngestionCommand command = new NewsIngestionCommand("기준금리", 0, 20, NewsSort.RECENCY);
        when(newsIngestionService.ingest(command)).thenReturn(new NewsIngestionResult(3, 2, 1, 0));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(3))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        verify(newsIngestionService).ingest(command);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"query\":null,\"page\":0,\"size\":20,\"sort\":\"RECENCY\"}",
            "{\"query\":\"\",\"page\":0,\"size\":20,\"sort\":\"RECENCY\"}",
            "{\"query\":\"   \",\"page\":0,\"size\":20,\"sort\":\"RECENCY\"}",
            "{\"query\":\"금리\",\"page\":-1,\"size\":20,\"sort\":\"RECENCY\"}",
            "{\"query\":\"금리\",\"page\":0,\"size\":0,\"sort\":\"RECENCY\"}",
            "{\"query\":\"금리\",\"page\":0,\"size\":101,\"sort\":\"RECENCY\"}"
    })
    void invalidFieldsReturn400ErrorResponse(String json) throws Exception {
        assertInvalidRequest(json);
    }

    @Test
    void invalidSortReturns400WithoutEnumDetails() throws Exception {
        assertInvalidRequest("{\"query\":\"금리\",\"page\":0,\"size\":20,\"sort\":\"UNKNOWN\"}");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        assertInvalidRequest("{\"query\":");
    }

    @Test
    void unknownFieldReturns400() throws Exception {
        assertInvalidRequest("{\"query\":\"금리\",\"page\":0,\"size\":20,"
                + "\"sort\":\"RECENCY\",\"apiKey\":\"secret\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIMEOUT", "RATE_LIMITED", "TEMPORARY_FAILURE"})
    void retryableProviderFailuresReturn503WithoutProviderDetails(
            String errorType,
            CapturedOutput output
    ) throws Exception {
        NewsProviderException exception = new NewsProviderException(
                NewsProviderErrorType.valueOf(errorType),
                "secret-key provider body java.net.SocketTimeoutException"
        );
        when(newsIngestionService.ingest(new NewsIngestionCommand("기준금리", 0, 20, NewsSort.RECENCY)))
                .thenThrow(exception);

        mockMvc.perform(post(PATH)
                        .header("X-Request-Id", "provider-failure-request-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NEWS_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("News provider is temporarily unavailable."))
                .andExpect(jsonPath("$.message", not(containsString("secret-key"))))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        assertThat(output.getOut())
                .contains("news_provider_request_failed")
                .contains(errorType)
                .contains("provider-failure-request-1234")
                .doesNotContain("secret-key provider body")
                .doesNotContain("SocketTimeoutException");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_RESPONSE", "AUTHENTICATION_FAILED"})
    void nonRetryableProviderFailuresReturn502WithoutProviderDetails(String errorType) throws Exception {
        when(newsIngestionService.ingest(new NewsIngestionCommand("기준금리", 0, 20, NewsSort.RECENCY)))
                .thenThrow(new NewsProviderException(
                        NewsProviderErrorType.valueOf(errorType),
                        "external response with api-key"
                ));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("NEWS_PROVIDER_BAD_RESPONSE"))
                .andExpect(jsonPath("$.message").value("News provider returned an invalid response."))
                .andExpect(jsonPath("$.message", not(containsString("api-key"))));
    }

    @Test
    void controllerDependsOnlyOnIngestionServiceAndDoesNotExposeInfrastructureOrEntity() {
        Field[] fields = NewsSyncController.class.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(NewsIngestionService.class);
        assertThat(Arrays.stream(NewsSyncController.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(NewsArticle.class)))
                .isTrue();
        assertThat(Arrays.stream(fields).map(Field::getType))
                .doesNotContain(NewsArticleRepository.class, FakeNewsProvider.class);
    }

    private void assertInvalidRequest(String json) throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verifyNoInteractions(newsIngestionService);
    }

    private String validRequest() {
        return "{\"query\":\"기준금리\",\"page\":0,\"size\":20,\"sort\":\"RECENCY\"}";
    }
}
