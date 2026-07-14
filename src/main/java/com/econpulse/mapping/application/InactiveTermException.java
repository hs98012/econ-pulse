package com.econpulse.mapping.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class InactiveTermException extends BusinessException {

    public InactiveTermException() {
        super(ErrorCode.INACTIVE_TERM);
    }
}
