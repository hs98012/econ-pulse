package com.econpulse.news.api;

import com.econpulse.global.api.PageResponse;
import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.application.NewsPageQuery;
import com.econpulse.news.application.NewsQueryService;
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
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<NewsSummaryResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePageRequest(page, size);
        return ResponseEntity.ok(newsQueryService.findAll(new NewsPageQuery(page, size)));
    }

    @GetMapping("/{newsId}")
    public ResponseEntity<NewsDetailResponse> findById(@PathVariable @Positive Long newsId) {
        return ResponseEntity.ok(newsQueryService.findById(newsId));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > NewsPageQuery.MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
