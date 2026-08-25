package com.deployforge.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fails fast on broken configuration, warns loudly on incomplete configuration.
 *
 * <p>Hard failures (missing {@code JWT_SECRET} / {@code ENCRYPTION_KEY}) are enforced in the
 * constructors of {@code JwtService} and {@code EncryptionService}. This validator covers the rest:
 * port ranges, writability of the build root, unknown AI provider, and it reports which optional
 * integrations are inactive so nobody debugs a "broken" GitHub import that was simply never
 * configured.
 */
@Component
public class StartupConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigurationValidator.class);
    private static final Set<String> KNOWN_AI_PROVIDERS = Set.of("gemini", "openai", "heuristic");

    private final DeployForgeProperties properties;

    public StartupConfigurationValidator(DeployForgeProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void validate() {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        DeployForgeProperties.Deployment deployment = properties.deployment();

        if (deployment.portRangeEnd() <= deployment.portRangeStart()) {
            failures.add(
                    "deployforge.deployment.port-range-end must be greater than port-range-start");
        }
        if (deployment.portRangeEnd() - deployment.portRangeStart() < 10) {
            warnings.add(
                    "Fewer than 10 host ports are reserved for deployments; concurrent deployments will fail to bind");
        }
        if (deployment.cpuLimit() <= 0) {
            failures.add("deployforge.deployment.cpu-limit must be greater than 0");
        }

        Path buildRoot = Path.of(deployment.rootPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(buildRoot);
            if (!Files.isWritable(buildRoot)) {
                failures.add("Deployment root " + buildRoot + " is not writable");
            }
        } catch (IOException e) {
            failures.add("Deployment root " + buildRoot + " could not be created: " + e.getMessage());
        }

        String aiProvider = properties.ai().provider().toLowerCase(Locale.ROOT);
        if (!KNOWN_AI_PROVIDERS.contains(aiProvider)) {
            failures.add(
                    "AI_PROVIDER '"
                            + aiProvider
                            + "' is unknown. Supported providers: "
                            + String.join(", ", KNOWN_AI_PROVIDERS));
        }
        if ("gemini".equals(aiProvider) && !StringUtils.hasText(properties.ai().gemini().apiKey())) {
            warnings.add(
                    "AI_PROVIDER=gemini but GEMINI_API_KEY is empty - failure analysis will fall back to the local heuristic analyzer");
        }
        if ("openai".equals(aiProvider) && !StringUtils.hasText(properties.ai().openai().apiKey())) {
            warnings.add(
                    "AI_PROVIDER=openai but OPENAI_API_KEY is empty - failure analysis will fall back to the local heuristic analyzer");
        }
        if ("heuristic".equals(aiProvider)) {
            warnings.add(
                    "AI_PROVIDER=heuristic - deployment failures are analysed by the built-in rule based analyzer, no external model is called");
        }

        if (!properties.github().oauthConfigured()) {
            warnings.add(
                    "GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET are not set - GitHub login and repository import are disabled");
        }
        if (!properties.github().webhooksConfigured()) {
            warnings.add(
                    "GITHUB_WEBHOOK_SECRET is not set - incoming webhooks are rejected, so auto deploy on push is disabled");
        }
        if (!properties.security().cookieSecure()
                && properties.publicBackendUrl().startsWith("https://")) {
            warnings.add(
                    "COOKIE_SECURE=false while the backend is served over HTTPS - set COOKIE_SECURE=true in production");
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid DeployForge configuration:\n  - " + String.join("\n  - ", failures));
        }

        log.info(
                "deployforge_config build_root={} port_range={}-{} docker={} ai_provider={} github_oauth={} webhooks={}",
                buildRoot,
                deployment.portRangeStart(),
                deployment.portRangeEnd(),
                deployment.dockerHostUri(),
                aiProvider,
                properties.github().oauthConfigured(),
                properties.github().webhooksConfigured());
        warnings.forEach(warning -> log.warn("deployforge_config_warning {}", warning));
    }
}
