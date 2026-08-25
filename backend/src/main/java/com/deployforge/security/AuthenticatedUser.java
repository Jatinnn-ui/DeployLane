package com.deployforge.security;

import java.util.UUID;

/**
 * The authenticated principal placed in the {@code SecurityContext}.
 *
 * <p>Deliberately minimal: identity only. Workspace roles are never carried in the token because
 * they can change at any moment - every authorization decision re-reads membership from the database
 * through {@code AuthorizationService}.
 */
public record AuthenticatedUser(UUID userId, String githubUsername, String displayName) {

    @Override
    public String toString() {
        return "AuthenticatedUser[" + userId + "]";
    }
}
