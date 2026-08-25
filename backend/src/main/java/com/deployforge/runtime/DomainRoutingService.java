package com.deployforge.runtime;

import java.util.UUID;

/**
 * Decides how a running container becomes reachable.
 *
 * <p>The local implementation publishes a host port and returns {@code http://host:port}. A reverse
 * proxy implementation (Traefik / nginx) would instead return
 * {@code https://<slug>.<base-domain>} and reconfigure routing on promotion. Keeping this behind an
 * interface is what allows the deployment pipeline to be written once.
 */
public interface DomainRoutingService {

    /**
     * Makes a freshly started container reachable and returns its public URL.
     *
     * @param projectId owning project
     * @param environmentId owning environment
     * @param deploymentId the deployment whose container should receive traffic
     * @param projectSlug used to build a stable hostname where the strategy supports it
     * @param hostPort published host port, when the strategy uses port publishing
     */
    RoutingTarget publish(
            UUID projectId, UUID environmentId, UUID deploymentId, String projectSlug, int hostPort);

    /** Stops routing traffic to a deployment. Idempotent. */
    void unpublish(UUID environmentId, UUID deploymentId);

    /** The URL a deployment would be served on, without changing any routing. */
    String previewUrl(String projectSlug, int hostPort);

    record RoutingTarget(String url, String hostname, int port) {}
}
