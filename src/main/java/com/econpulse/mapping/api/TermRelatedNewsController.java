package com.econpulse.mapping.api;

import com.econpulse.global.api.PageResponse;
import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;
import com.econpulse.mapping.application.TermRelatedNewsQuery;
import com.econpulse.mapping.application.TermRelatedNewsQueryService;
import com.econpulse.mapping.application.TermRelatedNewsResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/terms")
public class TermRelatedNewsController {

    private final TermRelatedNewsQueryService queryService;

    public TermRelatedNewsController(TermRelatedNewsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{termId}/news")
    public ResponseEntity<PageResponse<TermRelatedNewsResponse>> find(
            @PathVariable @Positive Long termId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePageRequest(page, size);
        return ResponseEntity.ok(queryService.find(new TermRelatedNewsQuery(termId, page, size)));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > TermRelatedNewsQuery.MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
