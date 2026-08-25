package com.deployforge.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Single use, 60 second tickets that authenticate a WebSocket handshake.
 *
 * <p>The browser WebSocket API cannot send an {@code Authorization} header, and putting a 15 minute
 * access token in a URL means leaking it into proxy and server logs. Instead the SPA exchanges its
 * bearer token for a ticket over normal HTTPS and passes only the ticket in the handshake query.
 * The ticket is deleted the moment it is used.
 */
@Service
public class WebSocketTicketService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTicketService.class);
    private static final String KEY_PREFIX = "df:ws:ticket:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public WebSocketTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Ticket issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(KEY_PREFIX + ticket, userId.toString(), TTL);
        } catch (DataAccessException e) {
            log.error("ws_ticket_store_failed reason={}", e.getMessage());
            throw new com.deployforge.common.error.Exceptions.ExternalServiceException(
                    com.deployforge.common.error.ErrorCode.RUNTIME_UNAVAILABLE,
                    "Live log streaming is unavailable because Redis is down");
        }
        return new Ticket(ticket, TTL.toSeconds());
    }

    /** Consumes a ticket, returning the user it belonged to. */
    public Optional<UUID> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        try {
            String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
            return userId == null ? Optional.empty() : Optional.of(UUID.fromString(userId));
        } catch (DataAccessException | IllegalArgumentException e) {
            log.debug("ws_ticket_consume_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    public record Ticket(String ticket, long expiresInSeconds) {}
}
