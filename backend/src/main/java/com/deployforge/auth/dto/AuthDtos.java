package com.deployforge.auth.dto;

import com.deployforge.github.dto.GitHubDtos.GitHubConnectionStatus;
import com.deployforge.user.UserResponse;

/** Auth API payloads. */
public final class AuthDtos {

    private AuthDtos() {}

    /** Told to the login page so it can explain a missing OAuth configuration instead of failing. */
    public record AuthConfigResponse(boolean githubOauthEnabled, String loginUrl) {}

    public record SessionResponse(
            String accessToken,
            long expiresInSeconds,
            UserResponse user,
            GitHubConnectionStatus github) {}

    public record CurrentUserResponse(UserResponse user, GitHubConnectionStatus github) {}

    public record WebSocketTicketResponse(String ticket, long expiresInSeconds) {}
}
