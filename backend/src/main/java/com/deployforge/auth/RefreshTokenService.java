package com.deployforge.auth;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.UnauthenticatedException;
import com.deployforge.config.DeployForgeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Opaque, rotating refresh tokens stored in Redis.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li><b>Opaque, not a JWT.</b> Refresh tokens must be revocable; a stateless JWT is not.
 *   <li><b>Hashed at rest.</b> Only SHA-256 of the token is stored, so a Redis dump cannot be
 *       replayed.
 *   <li><b>Rotated on every use.</b> Each refresh invalidates the presented token, which turns token
 *       theft into a detectable, short lived problem.
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String KEY_PREFIX = "df:refresh:";
    private static final String USER_INDEX_PREFIX = "df:refresh:user:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redis, DeployForgeProperties properties) {
        this.redis = redis;
        this.ttl = properties.security().refreshTokenTtl();
    }

    public Duration ttl() {
        return ttl;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(KEY_PREFIX + hash(token), userId.toString(), ttl);
            redis.opsForSet().add(USER_INDEX_PREFIX + userId, hash(token));
            redis.expire(USER_INDEX_PREFIX + userId, ttl);
        } catch (DataAccessException e) {
            log.error("refresh_token_store_failed reason={}", e.getMessage());
            throw new UnauthenticatedException(
                    "Session storage is unavailable. Sign in again once the platform is healthy.");
        }
        return token;
    }

    /**
     * Validates and rotates a refresh token.
     *
     * @return the owning user id together with the replacement token
     * @throws UnauthenticatedException when the token is unknown, expired or already rotated
     */
    public Rotation rotate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw new UnauthenticatedException(ErrorCode.SESSION_EXPIRED, "No session cookie present");
        }
        String hashed = hash(presentedToken);
        String userId;
        try {
            userId = redis.opsForValue().getAndDelete(KEY_PREFIX + hashed);
        } catch (DataAccessException e) {
            log.error("refresh_token_lookup_failed reason={}", e.getMessage());
            throw new UnauthenticatedException("Session storage is unavailable");
        }
        if (userId == null) {
            throw new UnauthenticatedException(
                    ErrorCode.SESSION_EXPIRED, "Your session has expired. Sign in again.");
        }
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException("Session is corrupt. Sign in again.");
        }
        try {
            redis.opsForSet().remove(USER_INDEX_PREFIX + parsedUserId, hashed);
        } catch (DataAccessException e) {
            log.debug("refresh_index_cleanup_failed reason={}", e.getMessage());
        }
        return new Rotation(parsedUserId, issue(parsedUserId));
    }

    public void revoke(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        try {
            redis.delete(KEY_PREFIX + hash(presentedToken));
        } catch (DataAccessException e) {
            log.debug("refresh_token_revoke_failed reason={}", e.getMessage());
        }
    }

    /** Revokes every session of a user (used when a GitHub grant is removed). */
    public void revokeAll(UUID userId) {
        try {
            var hashes = redis.opsForSet().members(USER_INDEX_PREFIX + userId);
            if (hashes != null) {
                hashes.forEach(hashed -> redis.delete(KEY_PREFIX + hashed));
            }
            redis.delete(USER_INDEX_PREFIX + userId);
        } catch (DataAccessException e) {
            log.debug("refresh_token_revoke_all_failed reason={}", e.getMessage());
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    public record Rotation(UUID userId, String refreshToken) {}
}
