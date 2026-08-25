package com.deployforge.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request, exposes it through MDC (so it shows up in every log
 * line) and echoes it back in the {@code X-Request-Id} response header.
 *
 * <p>The same id is embedded in {@link com.deployforge.common.error.ApiErrorResponse} which makes a
 * user reported error traceable to exact log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = "deployforge.requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId =
                StringUtils.hasText(incoming) && incoming.length() <= 64
                        ? incoming.replaceAll("[^A-Za-z0-9\\-_]", "")
                        : UUID.randomUUID().toString().substring(0, 8);

        MDC.put(MDC_KEY, requestId);
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Never lose the id: fall back to MDC, then to "unknown". */
    public static String currentRequestId(HttpServletRequest request) {
        Object attribute = request == null ? null : request.getAttribute(ATTRIBUTE);
        if (attribute instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        String fromMdc = MDC.get(MDC_KEY);
        return StringUtils.hasText(fromMdc) ? fromMdc : "unknown";
    }
}
