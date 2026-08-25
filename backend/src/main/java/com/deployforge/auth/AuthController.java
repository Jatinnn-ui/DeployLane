package com.deployforge.auth;

import com.deployforge.auth.dto.AuthDtos.AuthConfigResponse;
import com.deployforge.auth.dto.AuthDtos.CurrentUserResponse;
import com.deployforge.auth.dto.AuthDtos.SessionResponse;
import com.deployforge.auth.dto.AuthDtos.WebSocketTicketResponse;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Flow: {@code /github/authorize} redirects to GitHub, {@code /github/callback} sets the HttpOnly
 * refresh cookie and bounces the browser back to the SPA, and the SPA calls {@code /refresh} to get an
 * in-memory access token. The access token is deliberately never placed in a URL or in
 * {@code localStorage}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "GitHub OAuth login, session refresh and logout")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;
    private final RefreshTokenService refreshTokenService;
    private final WebSocketTicketService ticketService;
    private final DeployForgeProperties properties;

    public AuthController(
            AuthService authService,
            AuthCookieFactory cookieFactory,
            RefreshTokenService refreshTokenService,
            WebSocketTicketService ticketService,
            DeployForgeProperties properties) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
        this.refreshTokenService = refreshTokenService;
        this.ticketService = ticketService;
        this.properties = properties;
    }

    @GetMapping("/config")
    @SecurityRequirements
    @Operation(summary = "Whether GitHub OAuth is configured on this server")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(
                authService.oauthEnabled(),
                properties.publicBackendUrl() + "/api/v1/auth/github/authorize");
    }

    @GetMapping("/github/authorize")
    @SecurityRequirements
    @Operation(
            summary = "Start the GitHub OAuth flow",
            description =
                    "Issues a single use state value, stores it in Redis and in an HttpOnly cookie, "
                            + "then redirects the browser to GitHub.")
    public ResponseEntity<Void> authorize(
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        String state = authService.beginLogin(returnTo);
        URI authorizeUri = authServiceAuthorizeUri(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.oauthStateCookie(state).toString())
                .location(authorizeUri)
                .build();
    }

    @GetMapping("/github/callback")
    @SecurityRequirements
    @Operation(
            summary = "GitHub OAuth callback",
            description =
                    "Validates state, exchanges the code, provisions the user and redirects back to the "
                            + "frontend with an HttpOnly refresh cookie set.")
    public void callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            @CookieValue(name = AuthCookieFactory.OAUTH_STATE_COOKIE, required = false) String stateCookie,
            HttpServletResponse response)
            throws java.io.IOException {

        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearedOauthStateCookie().toString());

        if (error != null) {
            // The user pressed "Cancel" on GitHub's consent screen, or GitHub refused.
            log.info("oauth_denied error={}", error);
            response.sendRedirect(frontendError(errorDescription == null ? error : errorDescription));
            return;
        }

        try {
            AuthService.LoginResult result = authService.completeLogin(code, state, stateCookie);
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    cookieFactory
                            .refreshCookie(result.refreshToken(), refreshTokenService.ttl())
                            .toString());
            response.sendRedirect(properties.frontendUrl() + result.returnPath());
        } catch (com.deployforge.common.error.ApiException e) {
            log.warn("oauth_callback_failed code={} message={}", e.getCode(), e.getMessage());
            response.sendRedirect(frontendError(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Exchange the refresh cookie for a new access token",
            description = "Rotates the refresh token. Called on app boot and shortly before token expiry.")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        ResponseCookie cookie =
                cookieFactory.refreshCookie(result.refreshToken(), refreshTokenService.ttl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.session());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke the current refresh token and clear the cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearedRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The authenticated user and their GitHub connection state")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user.userId());
    }

    @PostMapping("/ws-ticket")
    @Operation(
            summary = "Mint a single use WebSocket ticket",
            description =
                    "Valid for 60 seconds and consumed on first use. Required because browsers cannot send "
                            + "an Authorization header during a WebSocket handshake.")
    public WebSocketTicketResponse websocketTicket(@AuthenticationPrincipal AuthenticatedUser user) {
        WebSocketTicketService.Ticket ticket = ticketService.issue(user.userId());
        return new WebSocketTicketResponse(ticket.ticket(), ticket.expiresInSeconds());
    }

    private URI authServiceAuthorizeUri(String state) {
        return authService.authorizeUri(state);
    }

    private String frontendError(String message) {
        String safe = message == null ? "Login failed" : message;
        return properties.frontendUrl()
                + "/login?error="
                + URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }
}
