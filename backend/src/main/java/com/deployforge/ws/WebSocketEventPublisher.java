package com.deployforge.ws;

import com.deployforge.deployment.event.DeploymentEvent;
import com.deployforge.deployment.event.DeploymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WebSocket implementation of {@link DeploymentEventPublisher}.
 *
 * <p>Swallows its own failures by contract: a disconnected browser or a serialisation problem must never
 * propagate into the deployment pipeline.
 */
@Component
public class WebSocketEventPublisher implements DeploymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublisher.class);

    private final DeploymentWebSocketHandler handler;

    public WebSocketEventPublisher(DeploymentWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void publish(DeploymentEvent event) {
        try {
            handler.broadcast(event);
        } catch (RuntimeException e) {
            log.debug("event_broadcast_failed type={} reason={}", event.type(), e.getMessage());
        }
    }
}
