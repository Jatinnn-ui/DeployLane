package com.deployforge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed configuration for the whole platform.
 *
 * <p>Bound from the {@code deployforge.*} namespace. Anything security relevant (JWT secret,
 * encryption key, OAuth credentials, AI keys) is intentionally left empty by default and validated
 * at startup by {@link StartupConfigurationValidator} so misconfiguration fails loudly instead of
 * silently degrading.
 */
@ConfigurationProperties(prefix = "deployforge")
@Validated
public record DeployForgeProperties(
        @NotBlank String frontendUrl,
        @NotBlank String publicBackendUrl,
        @NotNull @Valid Security security,
        @NotNull @Valid Github github,
        @NotNull @Valid Ai ai,
        @NotNull @Valid Deployment deployment,
        @NotNull @Valid RateLimit rateLimit) {

    public record Security(
            String jwtSecret,
            String encryptionKey,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration oauthStateTtl,
            boolean cookieSecure,
            @NotBlank String cookieSameSite) {}

    public record Github(
            String clientId,
            String clientSecret,
            String webhookSecret,
            @NotBlank String apiBaseUrl,
            @NotBlank String oauthAuthorizeUrl,
            @NotBlank String oauthTokenUrl,
            @NotBlank String scopes) {

        /** OAuth login is only offered when both credentials are present. */
        public boolean oauthConfigured() {
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
        }

        public boolean webhooksConfigured() {
            return StringUtils.hasText(webhookSecret);
        }
    }

    public record Ai(
            @NotBlank String provider,
            @NotNull Duration requestTimeout,
            @Min(20) int maxLogLines,
            @NotNull @Valid Gemini gemini,
            @NotNull @Valid OpenAi openai) {

        public record Gemini(String apiKey, @NotBlank String model, @NotBlank String baseUrl) {}

        public record OpenAi(String apiKey, @NotBlank String model, @NotBlank String baseUrl) {}
    }

    public record Deployment(
            @NotBlank String rootPath,
            @NotBlank String publicHost,
            @Min(1024) int portRangeStart,
            @Min(1024) int portRangeEnd,
            @Min(128) long memoryLimitMb,
            double cpuLimit,
            @Min(16) long pidsLimit,
            @NotNull Duration buildTimeout,
            @NotNull Duration cloneTimeout,
            @Min(1) int workerConcurrency,
            @NotBlank String dockerHostUri,
            @NotBlank String dockerNetwork,
            String dockerApiVersion,
            @NotBlank String healthCheckPath,
            @NotNull Duration healthCheckTimeout,
            @NotNull Duration healthCheckInterval,
            @NotNull Duration metricsInterval,
            @Min(1) int buildRetentionHours,
            @Min(1) int logRetentionDays,
            @Min(1) int metricsRetentionHours,
            @NotBlank String routingMode,
            String baseDomain) {

        public long memoryLimitBytes() {
            return memoryLimitMb * 1024L * 1024L;
        }

        /** Whether Traefik-based subdomain routing is active. */
        public boolean isTraefikRouting() {
            return "traefik".equalsIgnoreCase(routingMode);
        }
    }

    public record RateLimit(
            @Min(1) int deploymentPerMinute,
            @Min(1) int aiAnalysisPerMinute,
            @Min(1) int aiChatPerMinute,
            @Min(1) int webhookPerMinute) {}
}
