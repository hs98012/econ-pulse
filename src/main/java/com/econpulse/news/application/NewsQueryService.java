package com.econpulse.news.application;

import com.econpulse.global.api.PageResponse;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsQueryService {

    private final NewsArticleRepository newsArticleRepository;

    public NewsQueryService(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NewsSummaryResponse> findAll(NewsPageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());
        return PageResponse.from(newsArticleRepository.findAllByOrderByPublishedAtDescIdDesc(pageable)
                .map(NewsSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse findById(Long newsId) {
        return newsArticleRepository.findById(newsId)
                .map(NewsDetailResponse::from)
                .orElseThrow(NewsNotFoundException::new);
    }
}
