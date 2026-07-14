package com.econpulse.news.api.internal;

import com.econpulse.news.application.NewsIngestionResult;
import com.econpulse.news.application.NewsIngestionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/news")
@ConditionalOnProperty(name = "econpulse.internal.news-sync.enabled", havingValue = "true")
public class NewsSyncController {

    private final NewsIngestionService newsIngestionService;

    public NewsSyncController(NewsIngestionService newsIngestionService) {
        this.newsIngestionService = newsIngestionService;
    }

    @PostMapping("/sync")
    public ResponseEntity<NewsSyncResponse> sync(@Valid @RequestBody NewsSyncRequest request) {
        NewsIngestionResult result = newsIngestionService.ingest(request.toCommand());
        return ResponseEntity.ok(NewsSyncResponse.from(result));
    }
}
