package com.deployforge.github.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire models for the GitHub REST API.
 *
 * <p>Every record is {@code ignoreUnknown} because GitHub adds fields continuously, and every
 * component is explicitly annotated so binding never depends on parameter name retention.
 */
public final class GitHubApiModels {

    private GitHubApiModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubUser(
            @JsonProperty("id") Long id,
            @JsonProperty("login") String login,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("type") String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubEmail(
            @JsonProperty("email") String email,
            @JsonProperty("primary") boolean primary,
            @JsonProperty("verified") boolean verified) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOwner(
            @JsonProperty("id") Long id,
            @JsonProperty("login") String login,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("type") String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubPermissions(
            @JsonProperty("admin") boolean admin,
            @JsonProperty("push") boolean push,
            @JsonProperty("pull") boolean pull) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubRepository(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("owner") GitHubOwner owner,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("clone_url") String cloneUrl,
            @JsonProperty("private") boolean isPrivate,
            @JsonProperty("description") String description,
            @JsonProperty("language") String language,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("pushed_at") Instant pushedAt,
            @JsonProperty("stargazers_count") Integer stargazersCount,
            @JsonProperty("archived") boolean archived,
            @JsonProperty("fork") boolean fork,
            @JsonProperty("permissions") GitHubPermissions permissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommitRef(
            @JsonProperty("sha") String sha, @JsonProperty("url") String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubBranch(
            @JsonProperty("name") String name,
            @JsonProperty("commit") GitHubCommitRef commit,
            @JsonProperty("protected") boolean isProtected) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommitAuthor(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("date") Instant date) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommitDetail(
            @JsonProperty("message") String message,
            @JsonProperty("author") GitHubCommitAuthor author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommit(
            @JsonProperty("sha") String sha,
            @JsonProperty("commit") GitHubCommitDetail commit,
            @JsonProperty("author") GitHubUser author,
            @JsonProperty("html_url") String htmlUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubContent(
            @JsonProperty("name") String name,
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("size") Long size,
            @JsonProperty("content") String content,
            @JsonProperty("encoding") String encoding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubHook(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("active") boolean active,
            @JsonProperty("events") List<String> events,
            @JsonProperty("config") Map<String, Object> config) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubAccessTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope,
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription) {}
}
