package com.econpulse.news.application;

import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;

public class NewsNotFoundException extends BusinessException {

    public NewsNotFoundException() {
        super(ErrorCode.NEWS_NOT_FOUND);
    }
}
