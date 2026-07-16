package com.econpulse.popular.application;

import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PopularTermQueryService {

    private final PopularTermStore popularTermStore;
    private final EconomicTermRepository economicTermRepository;

    public PopularTermQueryService(
            PopularTermStore popularTermStore,
            EconomicTermRepository economicTermRepository
    ) {
        this.popularTermStore = popularTermStore;
        this.economicTermRepository = economicTermRepository;
    }

    public List<PopularTermResponse> findPopularTerms(PopularTermQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Popular term query must not be null.");
        }
        List<PopularTermScore> scores = popularTermStore.findTop(query.date(), query.limit());
        if (scores.isEmpty()) {
            return List.of();
        }
        List<Long> ids = scores.stream()
                .map(PopularTermScore::economicTermId)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        Map<Long, EconomicTerm> activeTermsById = economicTermRepository
                .findAllByIdInAndStatus(ids, TermStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(EconomicTerm::getId, Function.identity()));

        List<PopularTermResponse> result = new ArrayList<>();
        for (PopularTermScore score : scores) {
            EconomicTerm term = activeTermsById.get(score.economicTermId());
            if (term != null) {
                result.add(new PopularTermResponse(
                        result.size() + 1,
                        term.getId(),
                        term.getName(),
                        term.getDefinition(),
                        score.score()
                ));
            }
        }
        return List.copyOf(result);
    }
}
