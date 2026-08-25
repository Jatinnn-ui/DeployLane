package com.deployforge.health;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.runtime.DeploymentRuntime;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated platform health used by the dashboard connectivity banner.
 *
 * <p>Distinct from Actuator: this endpoint is about "can DeployForge actually deploy right now",
 * so it reports the Docker engine and the build directory alongside the datastores. Each probe is
 * independent - one broken dependency degrades the report instead of failing it.
 */
@Service
public class PlatformHealthService {

    private static final Logger log = LoggerFactory.getLogger(PlatformHealthService.class);

    private final EntityManager entityManager;
    private final StringRedisTemplate redis;
    private final ObjectProvider<DeploymentRuntime> runtimeProvider;
    private final DeployForgeProperties properties;
    private final Instant startedAt = Instant.now();

    public PlatformHealthService(
            EntityManager entityManager,
            StringRedisTemplate redis,
            ObjectProvider<DeploymentRuntime> runtimeProvider,
            DeployForgeProperties properties) {
        this.entityManager = entityManager;
        this.redis = redis;
        this.runtimeProvider = runtimeProvider;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PlatformHealthResponse check() {
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("database", probeDatabase());
        components.put("redis", probeRedis());
        components.put("docker", probeDocker());
        components.put("buildStorage", probeBuildStorage());

        boolean allUp = components.values().stream().allMatch(ComponentHealth::up);
        String status = allUp ? "UP" : "DEGRADED";

        return new PlatformHealthResponse(
                status,
                Instant.now(),
                Duration.between(startedAt, Instant.now()).toSeconds(),
                components);
    }

    private ComponentHealth probeDatabase() {
        long start = System.nanoTime();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return ComponentHealth.up("PostgreSQL reachable", elapsedMs(start));
        } catch (RuntimeException e) {
            log.warn("health_database_down reason={}", e.getMessage());
            return ComponentHealth.down("PostgreSQL unreachable", elapsedMs(start));
        }
    }

    private ComponentHealth probeRedis() {
        long start = System.nanoTime();
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            boolean ok = pong != null;
            return ok
                    ? ComponentHealth.up("Redis reachable", elapsedMs(start))
                    : ComponentHealth.down("Redis did not answer PING", elapsedMs(start));
        } catch (RuntimeException e) {
            log.warn("health_redis_down reason={}", e.getMessage());
            return ComponentHealth.down("Redis unreachable - deployments cannot be queued", elapsedMs(start));
        }
    }

    private ComponentHealth probeDocker() {
        long start = System.nanoTime();
        DeploymentRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) {
            return ComponentHealth.down("No deployment runtime configured", elapsedMs(start));
        }
        DeploymentRuntime.RuntimeAvailability availability = runtime.checkAvailability();
        return availability.available()
                ? ComponentHealth.up(availability.detail(), elapsedMs(start))
                : ComponentHealth.down(availability.detail(), elapsedMs(start));
    }

    private ComponentHealth probeBuildStorage() {
        long start = System.nanoTime();
        Path root = Path.of(properties.deployment().rootPath()).toAbsolutePath().normalize();
        if (Files.isDirectory(root) && Files.isWritable(root)) {
            return ComponentHealth.up("Build directory writable", elapsedMs(start));
        }
        return ComponentHealth.down("Build directory " + root + " is not writable", elapsedMs(start));
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** One dependency's state. {@code detail} is safe to show in the UI. */
    public record ComponentHealth(boolean up, String status, String detail, long latencyMs) {
        static ComponentHealth up(String detail, long latencyMs) {
            return new ComponentHealth(true, "UP", detail, latencyMs);
        }

        static ComponentHealth down(String detail, long latencyMs) {
            return new ComponentHealth(false, "DOWN", detail, latencyMs);
        }
    }

    public record PlatformHealthResponse(
            String status, Instant timestamp, long uptimeSeconds, Map<String, ComponentHealth> components) {}
}
