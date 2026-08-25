package com.deployforge.deployment.event;

/**
 * Publishes deployment events to connected clients.
 *
 * <p>An interface rather than a direct WebSocket dependency: the pipeline and log service should not
 * know the transport, and publishing must never be able to break a deployment. Implementations are
 * expected to swallow their own transport errors.
 */
public interface DeploymentEventPublisher {

    void publish(DeploymentEvent event);
}
