package com.econpulse.mapping.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.application.AutoMapNewsCommand;
import com.econpulse.mapping.application.AutoMapNewsResult;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import com.econpulse.mapping.application.TermNewsMappingService;
import com.econpulse.mapping.domain.TermNewsMatcher;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.application.NewsNotFoundException;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = AutoTermNewsMappingController.class,
        properties = "econpulse.internal.term-news-mapping.enabled=true"
)
class AutoTermNewsMappingControllerTest {

    private static final String PATH = "/internal/api/v1/news/{newsId}/term-mappings/auto";
    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private TermNewsAutoMappingService autoMappingService;

    @Autowired
    AutoTermNewsMappingControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @MethodSource("successfulResults")
    void validNewsIdReturnsApplicationCounts(AutoMapNewsResult result) throws Exception {
        AutoMapNewsCommand command = new AutoMapNewsCommand(result.newsArticleId());
        when(autoMappingService.mapNews(command)).thenReturn(result);

        mockMvc.perform(post(PATH, result.newsArticleId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsArticleId").value(result.newsArticleId()))
                .andExpect(jsonPath("$.evaluatedTerms").value(result.evaluatedTerms()))
                .andExpect(jsonPath("$.matchedCandidates").value(result.matchedCandidates()))
                .andExpect(jsonPath("$.created").value(result.created()))
                .andExpect(jsonPath("$.updated").value(result.updated()))
                .andExpect(jsonPath("$.skipped").value(result.skipped()))
                .andExpect(jsonPath("$.noMatch").value(result.noMatch()))
                .andExpect(jsonPath("$.mappings").doesNotExist());

        verify(autoMappingService).mapNews(command);
        assertThat(result.evaluatedTerms()).isEqualTo(result.matchedCandidates() + result.noMatch());
        assertThat(result.matchedCandidates())
                .isEqualTo(result.created() + result.updated() + result.skipped());
    }

    static Stream<Arguments> successfulResults() {
        return Stream.of(
                Arguments.of(new AutoMapNewsResult(15L, 5, 3, 3, 0, 0, 2)),
                Arguments.of(new AutoMapNewsResult(15L, 5, 3, 0, 1, 2, 2)),
                Arguments.of(new AutoMapNewsResult(15L, 3, 3, 0, 0, 3, 0)),
                Arguments.of(new AutoMapNewsResult(15L, 0, 0, 0, 0, 0, 0)),
                Arguments.of(new AutoMapNewsResult(15L, 4, 0, 0, 0, 0, 4))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void invalidNewsIdReturns400WithoutCallingService(String newsId) throws Exception {
        mockMvc.perform(post(PATH, newsId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verifyNoInteractions(autoMappingService);
    }

    @Test
    void missingNewsReturnsSanitized404() throws Exception {
        when(autoMappingService.mapNews(new AutoMapNewsCommand(999L)))
                .thenThrow(new NewsNotFoundException());

        mockMvc.perform(post(PATH, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("News article was not found."))
                .andExpect(jsonPath("$.message", not(containsString("NewsNotFoundException"))))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void unexpectedFailureReturnsSanitized500() throws Exception {
        when(autoMappingService.mapNews(new AutoMapNewsCommand(1L)))
                .thenThrow(new IllegalStateException("SQL secret_table internal.ClassName"));

        mockMvc.perform(post(PATH, 1L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(jsonPath("$.message", not(containsString("secret_table"))))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void controllerDependsOnlyOnAutoMappingServiceAndExposesNoEntity() {
        Field[] fields = AutoTermNewsMappingController.class.getDeclaredFields();

        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(TermNewsAutoMappingService.class);
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(
                EconomicTermRepository.class,
                NewsArticleRepository.class,
                TermNewsMappingRepository.class,
                TermNewsMappingService.class,
                TermNewsMatcher.class
        );
        assertThat(Arrays.stream(AutoTermNewsMappingController.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(NewsArticle.class)))
                .isTrue();
    }
}
