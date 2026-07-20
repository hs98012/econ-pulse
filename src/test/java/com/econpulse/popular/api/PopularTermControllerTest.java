package com.econpulse.popular.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.popular.application.PopularTermQueryService;
import com.econpulse.popular.application.PopularTermResponse;
import com.econpulse.popular.application.PopularTermService;
import com.econpulse.popular.application.port.PopularTermStoreException;
import com.econpulse.popular.domain.PopularTermSnapshot;
import com.econpulse.popular.infrastructure.PopularTermSnapshotRepository;
import com.econpulse.popular.infrastructure.redis.RedisPopularTermStore;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PopularTermController.class)
class PopularTermControllerTest {

    private static final String PATH = "/api/v1/terms/popular";
    private static final String UTC_INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private final MockMvc mockMvc;

    @MockitoBean
    private PopularTermQueryService queryService;

    @Autowired
    PopularTermControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void omittedLimitUsesTenAndPreservesApplicationOrder() throws Exception {
        when(queryService.findTodayPopularTerms(10)).thenReturn(List.of(
                response(1, 12, "기준금리", "기준금리 정의", 25),
                response(2, 4, "환율", "환율 정의", 18)
        ));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].economicTermId").value(12))
                .andExpect(jsonPath("$[0].name").value("기준금리"))
                .andExpect(jsonPath("$[0].description").value("기준금리 정의"))
                .andExpect(jsonPath("$[0].score").value(25))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].economicTermId").value(4))
                .andExpect(jsonPath("$[0].aliases").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$[0].redisKey").doesNotExist());

        verify(queryService).findTodayPopularTerms(10);
    }

    @Test
    void specifiedLimitIsPassedAndEmptyResultIsArray() throws Exception {
        when(queryService.findTodayPopularTerms(3)).thenReturn(List.of());

        mockMvc.perform(get(PATH).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(queryService).findTodayPopularTerms(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "text"})
    void invalidLimitReturns400WithoutCallingQueryService(String limit) throws Exception {
        mockMvc.perform(get(PATH).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));

        verifyNoInteractions(queryService);
    }

    @Test
    void redisUnavailableReturnsSanitized503InsteadOfEmptyArray() throws Exception {
        when(queryService.findTodayPopularTerms(10)).thenThrow(new PopularTermStoreException(
                PopularTermStoreException.Reason.UNAVAILABLE,
                "RedisCommandTimeoutException redis://secret-host:6379"
        ));

        mockMvc.perform(get(PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POPULAR_TERM_STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Popular term service is temporarily unavailable."))
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(content().string(not(containsString("RedisCommandTimeoutException"))))
                .andExpect(content().string(not(containsString("secret-host"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void mysqlFailureUsesCommon500AndIsNotHiddenAsEmptyArray() throws Exception {
        when(queryService.findTodayPopularTerms(10)).thenThrow(new RuntimeException("mysql host secret"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(content().string(not(containsString("mysql host secret"))));
    }

    @Test
    void controllerDependsOnlyOnQueryServiceAndDoesNotReturnEntities() {
        Field[] fields = PopularTermController.class.getDeclaredFields();

        assertThat(fields).hasSize(2);
        assertThat(Arrays.stream(fields).filter(field -> !field.getType().equals(String.class)))
                .allMatch(field -> field.getType().equals(PopularTermQueryService.class));
        assertThat(Arrays.stream(fields).map(Field::getType)).doesNotContain(
                RedisPopularTermStore.class,
                EconomicTermRepository.class,
                PopularTermService.class,
                PopularTermSnapshotRepository.class,
                EconomicTerm.class,
                PopularTermSnapshot.class
        );
        assertThat(Arrays.stream(PopularTermController.class.getDeclaredMethods()))
                .noneMatch(method -> method.getReturnType().equals(EconomicTerm.class));
    }

    private PopularTermResponse response(int rank, long id, String name, String description, long score) {
        return new PopularTermResponse(rank, id, name, description, score);
    }
}
