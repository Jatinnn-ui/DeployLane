package com.deployforge.github.dto;

import com.deployforge.github.api.GitHubApiModels.GitHubBranch;
import com.deployforge.github.api.GitHubApiModels.GitHubCommit;
import com.deployforge.github.api.GitHubApiModels.GitHubRepository;
import java.time.Instant;
import java.util.List;

/** DeployForge's own view of GitHub data. Never leaks tokens or raw API payloads. */
public final class GitHubDtos {

    private GitHubDtos() {}

    public record RepositorySummary(
            Long id,
            String name,
            String fullName,
            String owner,
            String description,
            String language,
            boolean isPrivate,
            String defaultBranch,
            String htmlUrl,
            String cloneUrl,
            Instant updatedAt,
            Instant pushedAt,
            Integer stars,
            boolean archived,
            boolean fork,
            boolean canAdmin) {

        public static RepositorySummary from(GitHubRepository repository) {
            return new RepositorySummary(
                    repository.id(),
                    repository.name(),
                    repository.fullName(),
                    repository.owner() == null ? null : repository.owner().login(),
                    repository.description(),
                    repository.language(),
                    repository.isPrivate(),
                    repository.defaultBranch(),
                    repository.htmlUrl(),
                    repository.cloneUrl(),
                    repository.updatedAt(),
                    repository.pushedAt(),
                    repository.stargazersCount(),
                    repository.archived(),
                    repository.fork(),
                    repository.permissions() != null && repository.permissions().admin());
        }
    }

    public record BranchSummary(String name, String commitSha, boolean isProtected) {

        public static BranchSummary from(GitHubBranch branch) {
            return new BranchSummary(
                    branch.name(),
                    branch.commit() == null ? null : branch.commit().sha(),
                    branch.isProtected());
        }
    }

    public record CommitSummary(
            String sha,
            String shortSha,
            String message,
            String authorName,
            String authorAvatarUrl,
            Instant committedAt,
            String htmlUrl) {

        public static CommitSummary from(GitHubCommit commit) {
            String message =
                    commit.commit() == null || commit.commit().message() == null
                            ? ""
                            : commit.commit().message();
            String firstLine = message.lines().findFirst().orElse("");
            return new CommitSummary(
                    commit.sha(),
                    commit.sha() == null ? null : commit.sha().substring(0, Math.min(7, commit.sha().length())),
                    firstLine,
                    commit.commit() == null || commit.commit().author() == null
                            ? null
                            : commit.commit().author().name(),
                    commit.author() == null ? null : commit.author().avatarUrl(),
                    commit.commit() == null || commit.commit().author() == null
                            ? null
                            : commit.commit().author().date(),
                    commit.htmlUrl());
        }
    }

    public record RepositoryListResponse(
            List<RepositorySummary> repositories, int page, int perPage, boolean hasMore, int total) {}

    public record GitHubConnectionStatus(
            boolean connected,
            String githubUsername,
            String scopes,
            boolean hasRepoScope,
            Instant connectedAt) {

        public static GitHubConnectionStatus disconnected() {
            return new GitHubConnectionStatus(false, null, null, false, null);
        }
    }
}
