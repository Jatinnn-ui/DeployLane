package com.deployforge.security;

import com.deployforge.common.error.ApiErrorResponse;
import com.deployforge.common.error.ApiException;
import com.deployforge.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a {@code Authorization: Bearer <jwt>} header into an authenticated SecurityContext.
 *
 * <p>Absent header means "anonymous" and is left for the authorization rules to reject. A
 * <em>present but invalid</em> header fails immediately with a structured error so the frontend can
 * distinguish "not logged in" from "token expired, refresh me".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AuthenticatedUser user = jwtService.verifyAccessToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (ApiException e) {
            SecurityContextHolder.clearContext();
            writeError(request, response, e);
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ApiException e)
            throws IOException {
        response.setStatus(e.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body =
                ApiErrorResponse.of(
                        e.getStatus().value(),
                        e.getCode(),
                        e.getMessage(),
                        RequestIdFilter.currentRequestId(request),
                        request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
