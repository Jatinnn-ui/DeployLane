package com.deployforge.ws;

import com.deployforge.auth.WebSocketTicketService;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.workspace.Permission;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates and authorizes a WebSocket handshake.
 *
 * <p>Browsers cannot set an {@code Authorization} header on a WebSocket, so the SPA first exchanges its
 * bearer token for a single use ticket over HTTPS and passes that here. The ticket is consumed on first
 * use and expires after 60 seconds, which keeps a long lived credential out of URLs and proxy logs.
 *
 * <p>Authorization is enforced at the handshake, not per message: the connection is scoped to one
 * deployment, and the caller must hold {@code VIEW_LOGS} on that deployment's project.
 */
@Component
public class DeploymentWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(DeploymentWebSocketHandshakeInterceptor.class);

    public static final String ATTRIBUTE_USER_ID = "deployforge.userId";
    public static final String ATTRIBUTE_DEPLOYMENT_ID = "deployforge.deploymentId";

    private final WebSocketTicketService ticketService;
    private final DeploymentRepository deploymentRepository;
    private final ProjectAccessGuard accessGuard;

    public DeploymentWebSocketHandshakeInterceptor(
            WebSocketTicketService ticketService,
            DeploymentRepository deploymentRepository,
            ProjectAccessGuard accessGuard) {
        this.ticketService = ticketService;
        this.deploymentRepository = deploymentRepository;
        this.accessGuard = accessGuard;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {

        Optional<UUID> deploymentId = extractDeploymentId(request);
        if (deploymentId.isEmpty()) {
            reject(response, HttpStatus.BAD_REQUEST, "malformed deployment id");
            return false;
        }

        String ticket =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("ticket");
        Optional<UUID> userId = ticketService.consume(ticket);
        if (userId.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, "missing or expired ticket");
            return false;
        }

        Optional<Deployment> deployment = deploymentRepository.findById(deploymentId.get());
        if (deployment.isEmpty()) {
            reject(response, HttpStatus.NOT_FOUND, "unknown deployment");
            return false;
        }

        try {
            accessGuard.require(deployment.get().getProjectId(), userId.get(), Permission.VIEW_LOGS);
        } catch (RuntimeException e) {
            log.warn(
                    "ws_handshake_denied deployment={} user={} reason={}",
                    deploymentId.get(),
                    userId.get(),
                    e.getMessage());
            reject(response, HttpStatus.FORBIDDEN, "not authorized for this deployment");
            return false;
        }

        attributes.put(ATTRIBUTE_USER_ID, userId.get());
        attributes.put(ATTRIBUTE_DEPLOYMENT_ID, deploymentId.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // Nothing to do; failures are handled in beforeHandshake.
    }

    /** Last path segment of {@code /ws/deployments/{id}}. */
    private Optional<UUID> extractDeploymentId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(path.substring(lastSlash + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void reject(ServerHttpResponse response, HttpStatus status, String reason) {
        response.setStatusCode(status);
        log.debug("ws_handshake_rejected status={} reason={}", status.value(), reason);
    }
}
