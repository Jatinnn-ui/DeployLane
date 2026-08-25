package com.deployforge.security;

import com.deployforge.common.error.ApiErrorResponse;
import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.web.RequestIdFilter;
import com.deployforge.config.DeployForgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless HTTP security.
 *
 * <p><b>CSRF strategy.</b> Every state changing API call is authenticated with an
 * {@code Authorization: Bearer} header, which a cross-site form or image cannot set, so classic CSRF
 * does not apply and Spring's CSRF filter is disabled deliberately. Exactly two endpoints trust a
 * cookie - {@code /auth/refresh} and {@code /auth/logout} - and they are protected by a
 * {@code SameSite=Lax} HttpOnly cookie plus a strict CORS allow-list, so a third party site can
 * neither read the response nor use it to obtain a token. The webhook endpoint is authenticated by
 * an HMAC signature instead of a session.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DeployForgeProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DeployForgeProperties properties,
            ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(
                        headers ->
                                headers
                                        .frameOptions(frame -> frame.deny())
                                        .contentTypeOptions(Customizer.withDefaults())
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                org.springframework.security.web.header.writers
                                                                        .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                                                        .SAME_ORIGIN)))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Public: platform health + OAuth handshake + webhooks + docs
                                        .requestMatchers(
                                                "/api/v1/health",
                                                "/api/v1/health/**",
                                                "/api/v1/auth/github/**",
                                                "/api/v1/auth/refresh",
                                                "/api/v1/auth/logout",
                                                "/api/v1/auth/config",
                                                "/oauth2/authorization/github",
                                                "/api/v1/webhooks/**",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/api/docs",
                                                "/api/docs/**")
                                        .permitAll()
                                        // The WebSocket handshake authenticates with a single use ticket
                                        .requestMatchers("/ws/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        handling ->
                                handling
                                        .authenticationEntryPoint(
                                                (request, response, ex) ->
                                                        write(
                                                                request,
                                                                response,
                                                                HttpStatus.UNAUTHORIZED,
                                                                ErrorCode.UNAUTHENTICATED,
                                                                "Authentication is required"))
                                        .accessDeniedHandler(
                                                (request, response, ex) ->
                                                        write(
                                                                request,
                                                                response,
                                                                HttpStatus.FORBIDDEN,
                                                                ErrorCode.ACCESS_DENIED,
                                                                "Access denied")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Strict allow-list. Credentials are only meaningful for the refresh cookie.
        configuration.setAllowedOrigins(List.of(properties.frontendUrl()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Request-Id", "Accept"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }

    private void write(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        status.value(),
                        code,
                        message,
                        RequestIdFilter.currentRequestId(request),
                        request.getRequestURI()));
    }
}
