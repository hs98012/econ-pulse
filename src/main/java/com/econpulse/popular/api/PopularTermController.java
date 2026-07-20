package com.econpulse.popular.api;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;
import com.econpulse.popular.application.PopularTermQuery;
import com.econpulse.popular.application.PopularTermQueryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terms")
public class PopularTermController {

    private static final String DEFAULT_LIMIT = "10";

    private final PopularTermQueryService popularTermQueryService;

    public PopularTermController(PopularTermQueryService popularTermQueryService) {
        this.popularTermQueryService = popularTermQueryService;
    }

    @GetMapping("/popular")
    public ResponseEntity<List<PopularTermApiResponse>> findTodayPopularTerms(
            @RequestParam(defaultValue = DEFAULT_LIMIT) int limit
    ) {
        validateLimit(limit);
        List<PopularTermApiResponse> response = popularTermQueryService.findTodayPopularTerms(limit)
                .stream()
                .map(PopularTermApiResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > PopularTermQuery.MAX_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
