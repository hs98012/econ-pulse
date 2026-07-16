package com.econpulse.popular.application.port;

import com.econpulse.popular.application.PopularTermScore;
import java.time.LocalDate;
import java.util.List;

public interface PopularTermStore {

    void increment(long economicTermId, LocalDate date);

    List<PopularTermScore> findTop(LocalDate date, int limit);
}
