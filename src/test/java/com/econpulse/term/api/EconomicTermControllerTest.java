package com.econpulse.term.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class EconomicTermControllerTest extends AbstractIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final EconomicTermRepository economicTermRepository;
    private final EconomicTermAliasRepository economicTermAliasRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final TermNewsMappingRepository termNewsMappingRepository;

    @Autowired
    EconomicTermControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            EconomicTermRepository economicTermRepository,
            EconomicTermAliasRepository economicTermAliasRepository,
            NewsArticleRepository newsArticleRepository,
            TermNewsMappingRepository termNewsMappingRepository
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.economicTermRepository = economicTermRepository;
        this.economicTermAliasRepository = economicTermAliasRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.termNewsMappingRepository = termNewsMappingRepository;
    }

    @BeforeEach
    void setUp() {
        termNewsMappingRepository.deleteAll();
        newsArticleRepository.deleteAll();
        economicTermAliasRepository.deleteAll();
        economicTermRepository.deleteAll();
    }

    @Test
    void postTermsReturns201WithLocationForCreatedTermPath() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TermCreateRequest("기준금리", "정의", List.of("정책금리")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/terms/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("기준금리"))
                .andExpect(jsonPath("$.latestNewsCount").value(0))
                .andExpect(jsonPath("$.createdAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.updatedAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andReturn();

        long termId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/terms/" + termId);
    }

    @Test
    void invalidCreateRequestReturns400ErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TermCreateRequest("", "정의", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingTermDetailReturns404ErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/terms/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void duplicateNormalizedNameReturns409ErrorResponse() throws Exception {
        create("GDP", List.of());

        mockMvc.perform(post("/api/v1/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TermCreateRequest("ｇｄｐ", "정의", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TERM_NAME"));
    }

    @Test
    void listWithoutQueryReturnsPagedActiveTerms() throws Exception {
        create("기준금리", List.of("정책금리"));
        create("물가", List.of("소비자물가"));

        mockMvc.perform(get("/api/v1/terms")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void blankQueryReturnsPagedActiveTerms() throws Exception {
        create("기준금리", List.of("정책금리"));
        create("물가", List.of("소비자물가"));

        mockMvc.perform(get("/api/v1/terms")
                        .param("query", "   ")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void nameSearchReturnsPagedMatchingTerms() throws Exception {
        create("기준금리", List.of("정책금리"));
        create("물가", List.of("소비자물가"));

        mockMvc.perform(get("/api/v1/terms")
                        .param("query", "기준")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("기준금리"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void aliasSearchReturnsPagedMatchingTerms() throws Exception {
        create("기준금리", List.of("정책금리"));
        create("물가", List.of("소비자물가"));

        mockMvc.perform(get("/api/v1/terms")
                        .param("query", "정책")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("기준금리"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void searchReturnsEachTermOnlyOnceWhenNameAndAliasBothMatch() throws Exception {
        create("기준금리", List.of("시장금리"));

        mockMvc.perform(get("/api/v1/terms")
                        .param("query", "금리")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void negativePageReturns400ErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/terms").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    @Test
    void zeroOrTooLargeSizeReturns400ErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/terms").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/terms").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deleteReturns204AndExcludesTermFromDetailListAndSearch() throws Exception {
        long termId = create("GDP", List.of());

        mockMvc.perform(delete("/api/v1/terms/{termId}", termId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/terms/{termId}", termId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/terms").param("query", "gdp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void summaryDoesNotExposeEntityFields() throws Exception {
        create("GDP", List.of("국내총생산"));

        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].normalizedName").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].newsMappings").doesNotExist())
                .andExpect(jsonPath("$.content[0].latestNewsCount").doesNotExist())
                .andExpect(jsonPath("$.content[0].aliases", not("[]")));
    }

    @Test
    void wrongPathVariableTypeReturns400ErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/terms/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void putTermsUpdatesTermAndReturnsUtcTimestamps() throws Exception {
        long termId = create("GDP", List.of("국내총생산"));

        mockMvc.perform(put("/api/v1/terms/{termId}", termId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TermUpdateRequest("국내총생산", "새 정의", List.of("gdp")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(termId))
                .andExpect(jsonPath("$.name").value("국내총생산"))
                .andExpect(jsonPath("$.definition").value("새 정의"))
                .andExpect(jsonPath("$.aliases[0]").value("gdp"))
                .andExpect(jsonPath("$.createdAt", matchesPattern(UTC_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.updatedAt", matchesPattern(UTC_INSTANT_PATTERN)));
    }

    private static final String UTC_INSTANT_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private long create(String name, List<String> aliases) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TermCreateRequest(name, "정의", aliases))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
