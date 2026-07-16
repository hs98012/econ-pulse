package com.econpulse.popular.application;

import com.econpulse.popular.application.port.PopularTermStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PopularTermService {

    private final PopularTermStore popularTermStore;
    private final Clock clock;

    public PopularTermService(PopularTermStore popularTermStore, Clock clock) {
        this.popularTermStore = popularTermStore;
        this.clock = clock;
    }

    public void recordSearch(RecordTermSearchCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Record term search command must not be null.");
        }
        popularTermStore.increment(command.economicTermId(), LocalDate.now(clock));
    }

    public List<PopularTermScore> findPopularTerms(PopularTermQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Popular term query must not be null.");
        }
        return List.copyOf(popularTermStore.findTop(query.date(), query.limit()));
    }
}
