package com.deployforge.ws;

import com.deployforge.config.DeployForgeProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket endpoint used for live deployment output.
 *
 * <p>Plain WebSocket rather than STOMP: there is exactly one message flow (server pushes events for one
 * deployment), so a broker protocol would add a dependency and a message router for no benefit. The
 * browser side is the native {@code WebSocket} API with no client library at all.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeploymentWebSocketHandler handler;
    private final DeploymentWebSocketHandshakeInterceptor handshakeInterceptor;
    private final DeployForgeProperties properties;

    public WebSocketConfig(
            DeploymentWebSocketHandler handler,
            DeploymentWebSocketHandshakeInterceptor handshakeInterceptor,
            DeployForgeProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(handler, "/ws/deployments/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(properties.frontendUrl());
    }
}
