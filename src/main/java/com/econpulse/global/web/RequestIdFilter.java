package com.econpulse.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final String COMPLETED_EVENT = "http_request_completed";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,128}");
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);

    private final Supplier<String> requestIdGenerator;

    public RequestIdFilter() {
        this(() -> UUID.randomUUID().toString());
    }

    RequestIdFilter(Supplier<String> requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        String previousRequestId = MDC.get(MDC_KEY);
        long startedAt = System.nanoTime();
        boolean unhandledFailure = false;
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            unhandledFailure = true;
            throw exception;
        } finally {
            int status = resolveStatus(response.getStatus(), unhandledFailure);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            logCompletion(request.getMethod(), request.getRequestURI(), status, durationMs);
            restoreMdc(previousRequestId);
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return requestIdGenerator.get();
    }

    private int resolveStatus(int responseStatus, boolean unhandledFailure) {
        if (unhandledFailure && responseStatus < HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return responseStatus;
    }

    private void logCompletion(String method, String path, int status, long durationMs) {
        var builder = status >= 500
                ? LOGGER.atError()
                : status >= 400 ? LOGGER.atWarn() : LOGGER.atInfo();
        builder.addKeyValue("event", COMPLETED_EVENT)
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log(COMPLETED_EVENT);
    }

    private void restoreMdc(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previousRequestId);
        }
    }
}
