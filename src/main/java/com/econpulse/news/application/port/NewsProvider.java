package com.econpulse.news.application.port;

public interface NewsProvider {

    NewsSearchResult search(NewsSearchQuery query);
}
