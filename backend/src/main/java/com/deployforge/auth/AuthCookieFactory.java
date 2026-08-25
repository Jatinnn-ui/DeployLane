package com.deployforge.auth;

import com.deployforge.config.DeployForgeProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the two cookies the auth flow needs.
 *
 * <p>Both are {@code HttpOnly} (JavaScript can never read them), scoped to {@code /api/v1/auth} so
 * they are not attached to ordinary API calls, and {@code SameSite=Lax} so they survive the top level
 * redirect back from GitHub while staying unavailable to cross-site sub-requests.
 */
@Component
public class AuthCookieFactory {

    public static final String REFRESH_COOKIE = "df_refresh";
    public static final String OAUTH_STATE_COOKIE = "df_oauth_state";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final DeployForgeProperties properties;

    public AuthCookieFactory(DeployForgeProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie refreshCookie(String token, Duration maxAge) {
        return base(REFRESH_COOKIE, token).maxAge(maxAge).build();
    }

    public ResponseCookie clearedRefreshCookie() {
        return base(REFRESH_COOKIE, "").maxAge(Duration.ZERO).build();
    }

    public ResponseCookie oauthStateCookie(String state) {
        return base(OAUTH_STATE_COOKIE, state)
                .maxAge(properties.security().oauthStateTtl())
                .build();
    }

    public ResponseCookie clearedOauthStateCookie() {
        return base(OAUTH_STATE_COOKIE, "").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .path(COOKIE_PATH)
                .sameSite(properties.security().cookieSameSite());
    }
}
