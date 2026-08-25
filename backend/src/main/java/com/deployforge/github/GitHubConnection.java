package com.deployforge.github;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A user's GitHub OAuth grant.
 *
 * <p>The access token is stored encrypted (AES-256-GCM) and never leaves the backend: the frontend
 * calls DeployForge, DeployForge calls GitHub.
 */
@Entity
@Table(name = "github_connections")
public class GitHubConnection extends AuditedEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "github_account_id", nullable = false)
    private Long githubAccountId;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "text")
    private String accessTokenEncrypted;

    @Column(name = "token_type", length = 32)
    private String tokenType;

    @Column(name = "scopes", length = 512)
    private String scopes;

    protected GitHubConnection() {}

    public GitHubConnection(
            UUID userId,
            Long githubAccountId,
            String accessTokenEncrypted,
            String tokenType,
            String scopes) {
        this.userId = userId;
        this.githubAccountId = githubAccountId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.tokenType = tokenType;
        this.scopes = scopes;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getGithubAccountId() {
        return githubAccountId;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public void setAccessTokenEncrypted(String accessTokenEncrypted) {
        this.accessTokenEncrypted = accessTokenEncrypted;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    /** True when the grant includes the {@code repo} scope needed to clone private repositories. */
    public boolean hasRepoScope() {
        return scopes != null && scopes.contains("repo");
    }
}
