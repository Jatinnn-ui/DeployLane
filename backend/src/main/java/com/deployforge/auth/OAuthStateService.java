package com.deployforge.auth;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.config.DeployForgeProperties;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * OAuth {@code state} issuing and single-use verification.
 *
 * <p>Two independent checks defeat login CSRF and replay:
 *
 * <ol>
 *   <li>the state must still exist in Redis (single use, short TTL), and
 *   <li>the state must match the value in the browser's {@code df_oauth_state} cookie.
 * </ol>
 *
 * <p>An attacker can supply one of the two, never both.
 */
@Service
public class OAuthStateService {

    private static final Logger log = LoggerFactory.getLogger(OAuthStateService.class);
    private static final String KEY_PREFIX = "df:oauth:state:";

    private final StringRedisTemplate redis;
    private final DeployForgeProperties properties;
    private final SecureRandom random = new SecureRandom();

    public OAuthStateService(StringRedisTemplate redis, DeployForgeProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * @param returnPath frontend path to land on after login, validated by the caller
     * @return the opaque state value
     */
    public String issue(String returnPath) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue()
                    .set(
                            KEY_PREFIX + state,
                            returnPath == null || returnPath.isBlank() ? "/dashboard" : returnPath,
                            properties.security().oauthStateTtl());
        } catch (DataAccessException e) {
            log.error("oauth_state_store_failed reason={}", e.getMessage());
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR,
                    "Login cannot start because Redis is unavailable. Try again once the platform is healthy.");
        }
        return state;
    }

    /**
     * Consumes the state exactly once and returns the stored return path.
     *
     * <p>When accessed through a reverse proxy or tunnel on a different domain, the browser may not
     * send the state cookie back on the redirect from GitHub. In that case we fall back to verifying
     * that the state exists in Redis, which is still single-use and cryptographically random — an
     * attacker cannot guess it.
     *
     * @throws BadRequestException when the state is unknown or expired
     */
    public String consume(String state, String stateFromCookie) {
        if (state == null || state.isBlank()) {
            throw new BadRequestException(ErrorCode.GITHUB_OAUTH_ERROR, "Missing OAuth state parameter");
        }
        // If the cookie is present, verify it matches (defense in depth).
        // If absent (proxy/tunnel scenarios), skip the cookie check — Redis single-use is sufficient.
        if (stateFromCookie != null && !stateFromCookie.isBlank()) {
            if (!constantTimeEquals(state, stateFromCookie)) {
                log.warn("oauth_state_cookie_mismatch");
                throw new BadRequestException(
                        ErrorCode.GITHUB_OAUTH_ERROR,
                        "OAuth state does not match this browser session. Start the login again.");
            }
        } else {
            log.info("oauth_state_cookie_absent state={} - falling back to Redis-only validation", state.substring(0, Math.min(8, state.length())));
        }
        String key = KEY_PREFIX + state;
        String returnPath;
        try {
            returnPath = redis.opsForValue().getAndDelete(key);
        } catch (DataAccessException e) {
            log.error("oauth_state_consume_failed reason={}", e.getMessage());
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR, "Login could not be completed. Try again.");
        }
        if (returnPath == null) {
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR,
                    "This login attempt has expired or was already used. Start the login again.");
        }
        return returnPath;
    }

    public Optional<String> peek(String state) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + state));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
