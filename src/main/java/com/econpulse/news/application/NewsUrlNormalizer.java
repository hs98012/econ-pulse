package com.econpulse.news.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class NewsUrlNormalizer {

    public String normalize(String sourceUrl) {
        try {
            URI uri = new URI(sourceUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("sourceUrl must be absolute");
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = normalizePort(scheme, uri.getPort());

            return new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    null
            ).toASCIIString();
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IllegalArgumentException("sourceUrl must be a valid URI", exception);
        }
    }

    private int normalizePort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }
}
