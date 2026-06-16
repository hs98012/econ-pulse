package com.econpulse.term.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class TermNotFoundException extends BusinessException {

    public TermNotFoundException() {
        super(ErrorCode.TERM_NOT_FOUND);
    }
}
