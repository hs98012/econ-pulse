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

import com.econpulse.mapping.application.TermNewsAutoMappingCommand;
import com.econpulse.mapping.application.TermNewsAutoMappingResult;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = MappingRebuildController.class,
        properties = "econpulse.internal.mapping-rebuild.enabled=true"
)
class MappingRebuildControllerTest {

    private static final String PATH = "/internal/api/v1/mappings/rebuild";
    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private TermNewsAutoMappingService autoMappingService;

    @Autowired
    MappingRebuildControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void validSingleNewsRequestInvokesServiceAndReturnsEveryCount() throws Exception {
        TermNewsAutoMappingCommand command = TermNewsAutoMappingCommand.single(10L);
        when(autoMappingService.map(command)).thenReturn(result());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsArticleIds\":[10]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedNewsCount").value(3))
                .andExpect(jsonPath("$.processedNewsCount").value(3))
                .andExpect(jsonPath("$.activeTermCount").value(20))
                .andExpect(jsonPath("$.evaluatedPairCount").value(60))
                .andExpect(jsonPath("$.matchedCandidateCount").value(5))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.unmatchedPairCount").value(55));

        verify(autoMappingService).map(command);
    }

    @Test
    void multipleAndDuplicateIdsBecomeSortedUniqueApplicationCommand() throws Exception {
        TermNewsAutoMappingCommand command = new TermNewsAutoMappingCommand(java.util.List.of(3L, 1L, 2L, 1L));
        when(autoMappingService.map(command)).thenReturn(new TermNewsAutoMappingResult(
                3, 3, 0, 0, 0, 0, 0, 0, 0
        ));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsArticleIds\":[3,1,2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedNewsCount").value(3));

        verify(autoMappingService).map(new TermNewsAutoMappingCommand(java.util.List.of(1L, 2L, 3L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"newsArticleIds\":null}",
            "{\"newsArticleIds\":[]}",
            "{\"newsArticleIds\":[null]}",
            "{\"newsArticleIds\":[0]}",
            "{\"newsArticleIds\":[-1]}",
            "{\"newsArticleIds\":[\"1\"]}"
    })
    void invalidFieldsReturn400(String json) throws Exception {
        assertInvalidRequest(json);
    }

    @Test
    void oneHundredIdsAreAllowed() throws Exception {
        String json = idsRequest(100);
        TermNewsAutoMappingCommand command = new TermNewsAutoMappingCommand(
                IntStream.rangeClosed(1, 100).mapToObj(Long::valueOf).toList()
        );
        when(autoMappingService.map(command)).thenReturn(new TermNewsAutoMappingResult(
                100, 100, 0, 0, 0, 0, 0, 0, 0
        ));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void oneHundredOneIdsReturn400() throws Exception {
        assertInvalidRequest(idsRequest(101));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        assertInvalidRequest("{\"newsArticleIds\":");
    }

    @Test
    void unknownFieldAndAllRebuildRequestsReturn400() throws Exception {
        assertInvalidRequest("{\"newsArticleIds\":[1],\"all\":true}");
        assertInvalidRequest("{\"all\":true}");
    }

    @Test
    void missingNewsReturns404WithoutRequestedIds() throws Exception {
        TermNewsAutoMappingCommand command = new TermNewsAutoMappingCommand(java.util.List.of(1L, 999L));
        when(autoMappingService.map(command)).thenThrow(new NewsNotFoundException());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsArticleIds\":[1,999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("News article was not found."))
                .andExpect(jsonPath("$.message", not(containsString("999"))))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void unexpectedFailureReturnsSanitized500() throws Exception {
        TermNewsAutoMappingCommand command = TermNewsAutoMappingCommand.single(1L);
        when(autoMappingService.map(command))
                .thenThrow(new IllegalStateException("SQL secret_table internal.ClassName"));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsArticleIds\":[1]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(jsonPath("$.message", not(containsString("secret_table"))))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void controllerDependsOnlyOnAutoMappingServiceAndExposesNoEntity() {
        Field[] fields = MappingRebuildController.class.getDeclaredFields();

        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(TermNewsAutoMappingService.class);
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(
                EconomicTermRepository.class,
                NewsArticleRepository.class,
                TermNewsMappingRepository.class,
                TermNewsMappingService.class,
                TermNewsMatcher.class
        );
        assertThat(Arrays.stream(MappingRebuildController.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(NewsArticle.class)))
                .isTrue();
    }

    private void assertInvalidRequest(String json) throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verifyNoInteractions(autoMappingService);
    }

    private String idsRequest(int count) {
        String ids = IntStream.rangeClosed(1, count)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        return "{\"newsArticleIds\":[" + ids + "]}";
    }

    private TermNewsAutoMappingResult result() {
        return new TermNewsAutoMappingResult(3, 3, 20, 60, 5, 3, 1, 1, 55);
    }
}
