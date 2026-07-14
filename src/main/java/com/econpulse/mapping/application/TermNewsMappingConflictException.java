package com.econpulse.mapping.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class TermNewsMappingConflictException extends BusinessException {

    public TermNewsMappingConflictException() {
        super(ErrorCode.TERM_NEWS_MAPPING_CONFLICT);
    }
}
