package com.deployforge.ws;

import com.deployforge.deployment.event.DeploymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Fan-out of deployment events to browsers watching a specific deployment.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Sessions are wrapped in {@link ConcurrentWebSocketSessionDecorator} because a pipeline thread and
 *       a metrics thread can publish at the same time, and a raw session is not safe for concurrent
 *       sends.
 *   <li>The send buffer is bounded. A browser that stops reading (background tab, dead network) is
 *       disconnected rather than allowed to accumulate megabytes of build output in server memory.
 *   <li>Incoming frames are ignored apart from a {@code ping}; this channel is server to client only.
 * </ul>
 */
@Component
public class DeploymentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentWebSocketHandler.class);
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final Map<UUID, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public DeploymentWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID deploymentId = deploymentId(session);
        if (deploymentId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        WebSocketSession guarded =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        subscribers.computeIfAbsent(deploymentId, key -> new CopyOnWriteArraySet<>()).add(guarded);

        send(guarded, Map.of("type", "CONNECTED", "deploymentId", deploymentId.toString(), "timestamp", Instant.now().toString()));
        log.debug("ws_connected deployment={} subscribers={}", deploymentId, count(deploymentId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID deploymentId = deploymentId(session);
        if (deploymentId == null) {
            return;
        }
        Set<WebSocketSession> sessions = subscribers.get(deploymentId);
        if (sessions == null) {
            return;
        }
        sessions.removeIf(candidate -> sameSession(candidate, session));
        if (sessions.isEmpty()) {
            subscribers.remove(deploymentId);
        }
        log.debug("ws_disconnected deployment={} status={}", deploymentId, status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            send(session, Map.of("type", "PONG", "timestamp", Instant.now().toString()));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("ws_transport_error session={} reason={}", session.getId(), exception.getMessage());
        closeQuietly(session);
    }

    /** Broadcasts an event; never throws, because publishing must not affect a deployment. */
    public void broadcast(DeploymentEvent event) {
        Set<WebSocketSession> sessions = subscribers.get(event.deploymentId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.warn("ws_serialize_failed type={} reason={}", event.type(), e.getMessage());
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException | IllegalStateException e) {
                // IllegalStateException is the bounded buffer being exceeded: drop the slow consumer.
                log.debug("ws_send_failed session={} reason={}", session.getId(), e.getMessage());
                sessions.remove(session);
                closeQuietly(session);
            }
        }
    }

    public int count(UUID deploymentId) {
        Set<WebSocketSession> sessions = subscribers.get(deploymentId);
        return sessions == null ? 0 : sessions.size();
    }

    public List<UUID> watchedDeployments() {
        return List.copyOf(subscribers.keySet());
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            log.debug("ws_send_failed session={} reason={}", session.getId(), e.getMessage());
        }
    }

    private UUID deploymentId(WebSocketSession session) {
        Object value =
                session.getAttributes().get(DeploymentWebSocketHandshakeInterceptor.ATTRIBUTE_DEPLOYMENT_ID);
        return value instanceof UUID id ? id : null;
    }

    private boolean sameSession(WebSocketSession candidate, WebSocketSession session) {
        return candidate.getId().equals(session.getId());
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.trace("ws_close_failed session={}", session.getId());
        }
    }
}
