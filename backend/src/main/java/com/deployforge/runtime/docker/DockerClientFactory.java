package com.deployforge.runtime.docker;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.runtime.RuntimeExceptions.RuntimeUnavailableException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lazily creates and owns the single {@link DockerClient}.
 *
 * <p>Deliberately lazy: DeployForge must start and serve its API even when the Docker engine is
 * missing, so that the health endpoint can report exactly that instead of the whole application
 * failing to boot.
 *
 * <p>The response timeout is generous because image builds and log follows are long lived streams on
 * this connection; the per-operation budget is enforced by the pipeline, not by the socket.
 */
@Component
public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);

    private final DeployForgeProperties properties;
    private volatile DockerClient client;
    private volatile DockerHttpClient httpClient;

    public DockerClientFactory(DeployForgeProperties properties) {
        this.properties = properties;
    }

    public DockerClient client() {
        DockerClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                client = create();
            }
            return client;
        }
    }

    private DockerClient create() {
        String host = properties.deployment().dockerHostUri();
        try {
            DefaultDockerClientConfig.Builder builder =
                    DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(host);
            if (StringUtils.hasText(properties.deployment().dockerApiVersion())) {
                builder.withApiVersion(properties.deployment().dockerApiVersion());
            }
            DockerClientConfig config = builder.build();

            Duration responseTimeout =
                    properties.deployment().buildTimeout().plus(Duration.ofMinutes(5));
            httpClient =
                    new ApacheDockerHttpClient.Builder()
                            .dockerHost(config.getDockerHost())
                            .sslConfig(config.getSSLConfig())
                            .maxConnections(64)
                            .connectionTimeout(Duration.ofSeconds(15))
                            .responseTimeout(responseTimeout)
                            .build();

            log.info("docker_client_created host={} response_timeout={}", host, responseTimeout);
            return DockerClientImpl.getInstance(config, httpClient);
        } catch (RuntimeException e) {
            throw new RuntimeUnavailableException(
                    "Could not initialise a Docker client for '"
                            + host
                            + "'. On Windows use npipe:////./pipe/docker_engine, on Linux/macOS use unix:///var/run/docker.sock.",
                    e);
        }
    }

    @PreDestroy
    void shutdown() {
        DockerClient current = client;
        if (current != null) {
            try {
                current.close();
            } catch (IOException e) {
                log.debug("docker_client_close_failed reason={}", e.getMessage());
            }
        }
        DockerHttpClient currentHttp = httpClient;
        if (currentHttp != null) {
            try {
                currentHttp.close();
            } catch (IOException e) {
                log.debug("docker_http_close_failed reason={}", e.getMessage());
            }
        }
    }
}
