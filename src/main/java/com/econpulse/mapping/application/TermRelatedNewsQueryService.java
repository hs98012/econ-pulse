package com.econpulse.mapping.application;

import com.econpulse.global.api.PageResponse;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import com.econpulse.term.application.TermNotFoundException;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermRelatedNewsQueryService {

    private final EconomicTermRepository termRepository;
    private final TermNewsMappingRepository mappingRepository;

    public TermRelatedNewsQueryService(
            EconomicTermRepository termRepository,
            TermNewsMappingRepository mappingRepository
    ) {
        this.termRepository = termRepository;
        this.mappingRepository = mappingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TermRelatedNewsResponse> find(TermRelatedNewsQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        termRepository.findByIdAndStatus(query.termId(), TermStatus.ACTIVE)
                .orElseThrow(TermNotFoundException::new);

        PageRequest pageable = PageRequest.of(query.page(), query.size());
        return PageResponse.from(mappingRepository
                .findRelatedNewsByEconomicTermId(query.termId(), pageable)
                .map(TermRelatedNewsResponse::from));
    }
}
