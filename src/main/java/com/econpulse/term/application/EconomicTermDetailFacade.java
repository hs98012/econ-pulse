package com.econpulse.term.application;

import com.econpulse.popular.application.PopularTermService;
import com.econpulse.popular.application.RecordTermSearchCommand;
import com.econpulse.popular.application.port.PopularTermStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EconomicTermDetailFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(EconomicTermDetailFacade.class);

    private final EconomicTermService economicTermService;
    private final PopularTermService popularTermService;

    public EconomicTermDetailFacade(
            EconomicTermService economicTermService,
            PopularTermService popularTermService
    ) {
        this.economicTermService = economicTermService;
        this.popularTermService = popularTermService;
    }

    public EconomicTermDetailResult findByIdAndRecordView(Long termId) {
        EconomicTermDetailResult detail = economicTermService.findById(termId);
        try {
            popularTermService.recordSearch(new RecordTermSearchCommand(detail.id()));
        } catch (PopularTermStoreException exception) {
            if (exception.getReason() != PopularTermStoreException.Reason.UNAVAILABLE) {
                throw exception;
            }
            LOGGER.atWarn()
                    .addKeyValue("event", "popular_term_record_failed")
                    .addKeyValue("economicTermId", detail.id())
                    .addKeyValue("reason", "unavailable")
                    .log("popular_term_record_failed");
        }
        return detail;
    }
}
