package com.deployforge.runtime;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.ExternalServiceException;
import com.deployforge.config.DeployForgeProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands out host ports for published containers from the configured range.
 *
 * <p>Three checks, because any single one is insufficient:
 *
 * <ol>
 *   <li>ports already recorded on live deployments are excluded (the database is the record of intent)
 *   <li>a short Redis lease prevents two deployments that start seconds apart from choosing the same
 *       port before either container exists
 *   <li>the port is actually bound and released to confirm nothing outside DeployForge holds it
 * </ol>
 */
@Component
public class PortAllocator {

    private static final Logger log = LoggerFactory.getLogger(PortAllocator.class);
    private static final String LEASE_PREFIX = "df:port:";
    private static final Duration LEASE_TTL = Duration.ofMinutes(15);

    private final DeployForgeProperties properties;
    private final StringRedisTemplate redis;

    public PortAllocator(DeployForgeProperties properties, StringRedisTemplate redis) {
        this.properties = properties;
        this.redis = redis;
    }

    /**
     * @param portsInUse ports recorded against deployments that are still live
     * @param owner deployment id recorded on the lease, for debugging
     */
    public int allocate(Set<Integer> portsInUse, UUID owner) {
        int start = properties.deployment().portRangeStart();
        int end = properties.deployment().portRangeEnd();

        for (int port = start; port <= end; port++) {
            if (portsInUse.contains(port)) {
                continue;
            }
            if (!acquireLease(port, owner)) {
                continue;
            }
            if (!isBindable(port)) {
                releaseLease(port);
                continue;
            }
            log.debug("port_allocated port={} owner={}", port, owner);
            return port;
        }
        throw new ExternalServiceException(
                ErrorCode.RUNTIME_UNAVAILABLE,
                "No free host port between "
                        + start
                        + " and "
                        + end
                        + ". Stop unused deployments or widen DEPLOYMENT_PORT_RANGE_*.");
    }

    /** Called once a container owns the port, or when a deployment fails before binding. */
    public void releaseLease(int port) {
        try {
            redis.delete(LEASE_PREFIX + port);
        } catch (DataAccessException e) {
            log.debug("port_lease_release_failed port={} reason={}", port, e.getMessage());
        }
    }

    private boolean acquireLease(int port, UUID owner) {
        try {
            Boolean acquired =
                    redis
                            .opsForValue()
                            .setIfAbsent(LEASE_PREFIX + port, String.valueOf(owner), LEASE_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException e) {
            // Redis down: fall back to the bind test alone rather than blocking deployments.
            log.debug("port_lease_unavailable port={} reason={}", port, e.getMessage());
            return true;
        }
    }

    private boolean isBindable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
