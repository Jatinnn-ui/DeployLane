package com.deployforge.config;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP clients.
 *
 * <p>Every external call (GitHub API, AI providers, application health checks) gets an explicit
 * connect and read timeout. An un-timed-out client is how a deployment pipeline ends up hanging
 * forever.
 */
@Configuration
public class HttpClientConfig {

    public static final String GITHUB_CLIENT = "githubRestClient";
    public static final String GITHUB_OAUTH_CLIENT = "githubOAuthRestClient";
    public static final String AI_CLIENT = "aiRestClient";
    public static final String HEALTH_CHECK_CLIENT = "healthCheckRestClient";

    /** Token exchange lives on github.com, not api.github.com, hence a separate client. */
    @Bean(name = GITHUB_OAUTH_CLIENT)
    public RestClient githubOAuthRestClient() {
        return RestClient.builder()
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(15)))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "DeployForge-AI")
                .build();
    }

    @Bean(name = GITHUB_CLIENT)
    public RestClient githubRestClient(DeployForgeProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.github().apiBaseUrl())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(20)))
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "DeployForge-AI")
                .build();
    }

    @Bean(name = AI_CLIENT)
    public RestClient aiRestClient(DeployForgeProperties properties) {
        return RestClient.builder()
                .requestFactory(factory(Duration.ofSeconds(10), properties.ai().requestTimeout()))
                .defaultHeader("User-Agent", "DeployForge-AI")
                .build();
    }

    /**
     * Used to probe freshly started application containers. Short timeouts: a container that needs
     * more than five seconds to answer a single request is not healthy yet, and we will retry.
     */
    @Bean(name = HEALTH_CHECK_CLIENT)
    public RestClient healthCheckRestClient() {
        return RestClient.builder()
                .requestFactory(factory(Duration.ofSeconds(2), Duration.ofSeconds(5)))
                .defaultHeader("User-Agent", "DeployForge-HealthCheck")
                .build();
    }

    private ClientHttpRequestFactory factory(Duration connectTimeout, Duration readTimeout) {
        ClientHttpRequestFactorySettings settings =
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(readTimeout);
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
