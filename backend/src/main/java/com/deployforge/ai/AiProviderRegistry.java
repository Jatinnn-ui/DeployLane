package com.deployforge.ai;

import com.deployforge.ai.provider.HeuristicAiProvider;
import com.deployforge.config.DeployForgeProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Selects the active {@link AiProvider} and provides the local fallback.
 *
 * <p>Resolution is explicit rather than magical: the configured provider is used when it is actually
 * configured (has a key), otherwise the local heuristic analyzer takes over and says so. That means a
 * missing API key degrades one feature instead of breaking deployments, and the UI can always show
 * which analyzer produced a result.
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<String, AiProvider> providers;
    private final HeuristicAiProvider heuristicProvider;
    private final DeployForgeProperties properties;

    public AiProviderRegistry(
            List<AiProvider> providers,
            HeuristicAiProvider heuristicProvider,
            DeployForgeProperties properties) {
        this.providers =
                providers.stream()
                        .collect(Collectors.toMap(AiProvider::name, Function.identity(), (a, b) -> a));
        this.heuristicProvider = heuristicProvider;
        this.properties = properties;
    }

    /** The provider that should be tried first. */
    public AiProvider primary() {
        String configured = properties.ai().provider().toLowerCase(Locale.ROOT);
        AiProvider provider = providers.get(configured);
        if (provider == null) {
            log.warn("ai_provider_unknown configured={} falling_back=heuristic", configured);
            return heuristicProvider;
        }
        if (!provider.isConfigured()) {
            log.debug("ai_provider_not_configured provider={} falling_back=heuristic", configured);
            return heuristicProvider;
        }
        return provider;
    }

    /** Always available, never calls the network. */
    public AiProvider fallback() {
        return heuristicProvider;
    }

    public String configuredProviderName() {
        return properties.ai().provider().toLowerCase(Locale.ROOT);
    }

    public boolean externalProviderActive() {
        AiProvider provider = primary();
        return !provider.name().equals(heuristicProvider.name());
    }
}
