package com.econpulse.term.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class DuplicateTermNameException extends BusinessException {

    public DuplicateTermNameException() {
        super(ErrorCode.DUPLICATE_TERM_NAME);
    }
}
