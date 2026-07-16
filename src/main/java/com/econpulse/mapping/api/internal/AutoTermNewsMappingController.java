package com.econpulse.mapping.api.internal;

import com.econpulse.mapping.application.AutoMapNewsCommand;
import com.econpulse.mapping.application.AutoMapNewsResult;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/api/v1/news")
@ConditionalOnProperty(name = "econpulse.internal.term-news-mapping.enabled", havingValue = "true")
public class AutoTermNewsMappingController {

    private final TermNewsAutoMappingService autoMappingService;

    public AutoTermNewsMappingController(TermNewsAutoMappingService autoMappingService) {
        this.autoMappingService = autoMappingService;
    }

    @PostMapping("/{newsId}/term-mappings/auto")
    public ResponseEntity<AutoTermNewsMappingResponse> map(
            @PathVariable @Positive Long newsId
    ) {
        AutoMapNewsResult result = autoMappingService.mapNews(new AutoMapNewsCommand(newsId));
        return ResponseEntity.ok(AutoTermNewsMappingResponse.from(result));
    }
}
