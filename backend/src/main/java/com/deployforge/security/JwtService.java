package com.deployforge.security;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.UnauthenticatedException;
import com.deployforge.config.DeployForgeProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Issues and verifies the short lived access token (HS256).
 *
 * <p>Access tokens are bearer credentials held in browser memory only; long lived session
 * continuity comes from the rotating refresh token in an HttpOnly cookie. Keeping the access token
 * out of storage limits the blast radius of an XSS bug to the current tab lifetime.
 */
@Service
public class JwtService {

    private static final String ISSUER = "deployforge";
    private static final String CLAIM_GITHUB_USERNAME = "gh";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(DeployForgeProperties properties) {
        String secret = properties.security().jwtSecret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    """
                    JWT_SECRET is missing or too short.

                    HS256 requires at least 32 bytes of key material. Generate one with:

                        openssl rand -base64 48

                    then set JWT_SECRET in your .env file.""");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.security().accessTokenTtl();
    }

    public IssuedToken issueAccessToken(UUID userId, String githubUsername, String displayName) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String token =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject(userId.toString())
                        .claim(CLAIM_GITHUB_USERNAME, githubUsername)
                        .claim(CLAIM_NAME, displayName)
                        .claim(CLAIM_TYPE, TYPE_ACCESS)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key)
                        .compact();
        return new IssuedToken(token, expiresAt, accessTokenTtl.toSeconds());
    }

    /**
     * Verifies signature, expiry, issuer and token type.
     *
     * @throws UnauthenticatedException with {@code SESSION_EXPIRED} for an expired token so the
     *     frontend knows to silently refresh instead of logging the user out
     */
    public AuthenticatedUser verifyAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .requireIssuer(ISSUER)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new UnauthenticatedException("Token is not an access token");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            return new AuthenticatedUser(
                    userId,
                    claims.get(CLAIM_GITHUB_USERNAME, String.class),
                    claims.get(CLAIM_NAME, String.class));
        } catch (ExpiredJwtException e) {
            throw new UnauthenticatedException(ErrorCode.SESSION_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthenticatedException("Access token is invalid");
        }
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {}
}
