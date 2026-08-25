package com.deployforge.user;

import java.time.Instant;
import java.util.UUID;

/** Public representation of a user. No tokens, no e-mail of other users. */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        String githubUsername,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.displayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getGithubUsername(),
                user.getCreatedAt());
    }

    /** Member listings intentionally omit e-mail addresses. */
    public static UserResponse publicProfile(User user) {
        return new UserResponse(
                user.getId(),
                user.displayName(),
                null,
                user.getAvatarUrl(),
                user.getGithubUsername(),
                user.getCreatedAt());
    }
}
