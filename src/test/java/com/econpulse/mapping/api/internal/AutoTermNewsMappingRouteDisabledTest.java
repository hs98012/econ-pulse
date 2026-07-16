package com.econpulse.mapping.api.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.econpulse.mapping.application.TermNewsAutoMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = AutoTermNewsMappingController.class,
        properties = "econpulse.internal.term-news-mapping.enabled=false"
)
class AutoTermNewsMappingRouteDisabledTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private TermNewsAutoMappingService autoMappingService;

    @Autowired
    AutoTermNewsMappingRouteDisabledTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void disabledControllerPathReturns404() throws Exception {
        mockMvc.perform(post("/internal/api/v1/news/1/term-mappings/auto"))
                .andExpect(status().isNotFound());
    }
}
