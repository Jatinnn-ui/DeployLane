package com.deployforge.user;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A platform user, always originating from a GitHub identity.
 *
 * <p>{@code githubUserId} is the immutable GitHub account id and the real identity key - usernames
 * can be renamed and must never be used for lookups.
 */
@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "github_username", nullable = false)
    private String githubUsername;

    protected User() {}

    public User(Long githubUserId, String githubUsername, String name, String email, String avatarUrl) {
        this.githubUserId = githubUserId;
        this.githubUsername = githubUsername;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    /** Display name, falling back to the GitHub handle when the profile has no name set. */
    public String displayName() {
        return name == null || name.isBlank() ? githubUsername : name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getGithubUserId() {
        return githubUserId;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }
}
