package com.deployforge.auth;

import com.deployforge.auth.dto.AuthDtos.CurrentUserResponse;
import com.deployforge.auth.dto.AuthDtos.SessionResponse;
import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.github.GitHubService;
import com.deployforge.github.api.GitHubApiClient;
import com.deployforge.github.api.GitHubApiModels.GitHubAccessTokenResponse;
import com.deployforge.github.api.GitHubApiModels.GitHubUser;
import com.deployforge.security.JwtService;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.user.UserResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, session refresh and logout.
 *
 * <p>The OAuth callback does the minimum amount of work inside a transaction: the GitHub calls are
 * performed first, then user, GitHub grant and personal workspace are persisted together.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final GitHubOAuthClient oauthClient;
    private final GitHubApiClient githubApiClient;
    private final GitHubService githubService;
    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthStateService oauthStateService;
    private final DeployForgeProperties properties;

    public AuthService(
            GitHubOAuthClient oauthClient,
            GitHubApiClient githubApiClient,
            GitHubService githubService,
            UserRepository userRepository,
            UserProvisioningService userProvisioningService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            OAuthStateService oauthStateService,
            DeployForgeProperties properties) {
        this.oauthClient = oauthClient;
        this.githubApiClient = githubApiClient;
        this.githubService = githubService;
        this.userRepository = userRepository;
        this.userProvisioningService = userProvisioningService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.oauthStateService = oauthStateService;
        this.properties = properties;
    }

    public boolean oauthEnabled() {
        return properties.github().oauthConfigured();
    }

    public String beginLogin(String returnPath) {
        requireOauthConfigured();
        return oauthStateService.issue(sanitizeReturnPath(returnPath));
    }

    public java.net.URI authorizeUri(String state) {
        requireOauthConfigured();
        return oauthClient.authorizeUri(state);
    }

    /**
     * Completes the OAuth handshake.
     *
     * @return the login outcome: which user signed in, their fresh refresh token and where to send
     *     the browser
     */
    public LoginResult completeLogin(String code, String state, String stateCookie) {
        requireOauthConfigured();
        if (code == null || code.isBlank()) {
            throw new BadRequestException(ErrorCode.GITHUB_OAUTH_ERROR, "Missing authorization code");
        }
        String returnPath = oauthStateService.consume(state, stateCookie);

        GitHubAccessTokenResponse tokenResponse = oauthClient.exchangeCode(code);
        GitHubUser profile = githubApiClient.getAuthenticatedUser(tokenResponse.accessToken());
        if (profile == null || profile.id() == null) {
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR, "GitHub did not return a usable profile");
        }
        String email =
                profile.email() != null
                        ? profile.email()
                        : githubApiClient.getPrimaryEmail(tokenResponse.accessToken()).orElse(null);

        User user = userProvisioningService.provision(profile, email, tokenResponse);
        String refreshToken = refreshTokenService.issue(user.getId());

        log.info(
                "login_succeeded user={} github_login={} scopes={}",
                user.getId(),
                profile.login(),
                tokenResponse.scope());

        return new LoginResult(user.getId(), refreshToken, returnPath);
    }

    /** Rotates the refresh token and mints a new access token. */
    @Transactional(readOnly = true)
    public RefreshResult refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user =
                userRepository
                        .findById(rotation.userId())
                        .orElseThrow(
                                () -> {
                                    refreshTokenService.revokeAll(rotation.userId());
                                    return NotFoundException.of("User", rotation.userId());
                                });
        JwtService.IssuedToken accessToken =
                jwtService.issueAccessToken(user.getId(), user.getGithubUsername(), user.displayName());
        SessionResponse session =
                new SessionResponse(
                        accessToken.token(),
                        accessToken.expiresInSeconds(),
                        UserResponse.from(user),
                        githubService.connectionStatus(user.getId()));
        return new RefreshResult(session, rotation.refreshToken());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        return new CurrentUserResponse(UserResponse.from(user), githubService.connectionStatus(userId));
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private void requireOauthConfigured() {
        if (!oauthEnabled()) {
            throw new BadRequestException(
                    ErrorCode.GITHUB_OAUTH_ERROR,
                    "GitHub OAuth is not configured on this server. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET.");
        }
    }

    /**
     * Only same-site relative paths may be used as a post-login destination - otherwise the login
     * endpoint becomes an open redirect.
     */
    private String sanitizeReturnPath(String returnPath) {
        if (returnPath == null || returnPath.isBlank()) {
            return "/dashboard";
        }
        if (!returnPath.startsWith("/") || returnPath.startsWith("//") || returnPath.contains("://")) {
            return "/dashboard";
        }
        return returnPath.length() > 256 ? "/dashboard" : returnPath;
    }

    public record LoginResult(UUID userId, String refreshToken, String returnPath) {}

    public record RefreshResult(SessionResponse session, String refreshToken) {}
}
