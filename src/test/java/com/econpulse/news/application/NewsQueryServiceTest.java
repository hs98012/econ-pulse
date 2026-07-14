package com.econpulse.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.econpulse.global.api.PageResponse;
import com.econpulse.global.error.ErrorCode;
import com.econpulse.news.api.dto.NewsDetailResponse;
import com.econpulse.news.api.dto.NewsSummaryResponse;
import com.econpulse.news.domain.NewsArticle;
import com.econpulse.news.infrastructure.NewsArticleRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class NewsQueryServiceTest {

    private final NewsArticleRepository repository = Mockito.mock(NewsArticleRepository.class);
    private final NewsQueryService service = new NewsQueryService(repository);

    @Test
    void returnsRepositoryOrderAndPageMetadataAsSummaryDtos() {
        NewsArticle newest = article(2L, "최신 뉴스", "https://example.com/2", "2026-07-14T02:00:00");
        NewsArticle sameTimeHigherId = article(3L, "동시각 높은 ID", "https://example.com/3", "2026-07-14T01:00:00");
        PageRequest pageable = PageRequest.of(1, 2);
        when(repository.findAllByOrderByPublishedAtDescIdDesc(pageable)).thenReturn(
                new PageImpl<>(Arrays.asList(newest, sameTimeHigherId), pageable, 7)
        );

        PageResponse<NewsSummaryResponse> response = service.findAll(new NewsPageQuery(1, 2));

        assertThat(response.content()).extracting(NewsSummaryResponse::id).containsExactly(2L, 3L);
        assertThat(response.content().get(0).title()).isEqualTo("최신 뉴스");
        assertThat(response.content().get(0).summary()).isEqualTo("요약");
        assertThat(response.content().get(0).sourceName()).isEqualTo("Example News");
        assertThat(response.content().get(0).sourceUrl()).isEqualTo("https://example.com/2");
        assertThat(response.content().get(0).publishedAt()).isEqualTo(Instant.parse("2026-07-14T02:00:00Z"));
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(7);
        assertThat(response.totalPages()).isEqualTo(4);
        verify(repository).findAllByOrderByPublishedAtDescIdDesc(pageable);
    }

    @Test
    void returnsEmptyPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findAllByOrderByPublishedAtDescIdDesc(pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        PageResponse<NewsSummaryResponse> response = service.findAll(NewsPageQuery.defaults());

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void returnsDetailWithAllUtcTimestamps() {
        NewsArticle article = article(5L, "상세 뉴스", "https://example.com/5", "2026-07-14T01:00:00");
        when(article.getCollectedAt()).thenReturn(LocalDateTime.parse("2026-07-14T02:00:00"));
        when(article.getCreatedAt()).thenReturn(LocalDateTime.parse("2026-07-14T03:00:00"));
        when(article.getUpdatedAt()).thenReturn(LocalDateTime.parse("2026-07-14T04:00:00"));
        when(repository.findById(5L)).thenReturn(Optional.of(article));

        NewsDetailResponse response = service.findById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.publishedAt()).isEqualTo(Instant.parse("2026-07-14T01:00:00Z"));
        assertThat(response.collectedAt()).isEqualTo(Instant.parse("2026-07-14T02:00:00Z"));
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-14T03:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-07-14T04:00:00Z"));
    }

    @Test
    void throwsNewsNotFoundForUnknownId() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(NewsNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NEWS_NOT_FOUND);
    }

    @Test
    void summaryDtoDoesNotExposeInternalFields() {
        assertThat(Arrays.stream(NewsSummaryResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("sourceUrlHash", "collectedAt", "createdAt", "updatedAt", "termMappings");
    }

    private NewsArticle article(Long id, String title, String sourceUrl, String publishedAt) {
        NewsArticle article = Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(id);
        when(article.getTitle()).thenReturn(title);
        when(article.getSummary()).thenReturn("요약");
        when(article.getSourceName()).thenReturn("Example News");
        when(article.getSourceUrl()).thenReturn(sourceUrl);
        when(article.getPublishedAt()).thenReturn(LocalDateTime.parse(publishedAt));
        return article;
    }
}
