package com.econpulse.term.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class DuplicateTermAliasException extends BusinessException {

    public DuplicateTermAliasException() {
        super(ErrorCode.DUPLICATE_TERM_ALIAS);
    }
}
