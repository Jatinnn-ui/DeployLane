package com.deployforge.github.api;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.ExternalServiceException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.error.Exceptions.UnauthenticatedException;
import com.deployforge.config.HttpClientConfig;
import com.deployforge.github.api.GitHubApiModels.GitHubBranch;
import com.deployforge.github.api.GitHubApiModels.GitHubCommit;
import com.deployforge.github.api.GitHubApiModels.GitHubContent;
import com.deployforge.github.api.GitHubApiModels.GitHubEmail;
import com.deployforge.github.api.GitHubApiModels.GitHubHook;
import com.deployforge.github.api.GitHubApiModels.GitHubRepository;
import com.deployforge.github.api.GitHubApiModels.GitHubUser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin, typed wrapper over the GitHub REST API.
 *
 * <p>Responsibilities kept deliberately narrow: attach the caller's token, map transport and status
 * failures onto DeployForge's error model, and hand back typed records. No business logic, no
 * persistence.
 *
 * <p>Tokens are passed in per call. They are never logged, and never cached in this component.
 */
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final int MAX_PER_PAGE = 100;

    private final RestClient restClient;

    public GitHubApiClient(@Qualifier(HttpClientConfig.GITHUB_CLIENT) RestClient restClient) {
        this.restClient = restClient;
    }

    public GitHubUser getAuthenticatedUser(String token) {
        return call(
                "GET /user",
                () ->
                        restClient
                                .get()
                                .uri("/user")
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(GitHubUser.class));
    }

    /** Primary verified e-mail. GitHub hides it from {@code /user} unless it is public. */
    public Optional<String> getPrimaryEmail(String token) {
        try {
            List<GitHubEmail> emails =
                    call(
                            "GET /user/emails",
                            () ->
                                    restClient
                                            .get()
                                            .uri("/user/emails")
                                            .headers(headers -> authorize(headers, token))
                                            .retrieve()
                                            .body(new ParameterizedTypeReference<List<GitHubEmail>>() {}));
            if (emails == null) {
                return Optional.empty();
            }
            return emails.stream()
                    .filter(email -> email.primary() && email.verified())
                    .map(GitHubEmail::email)
                    .findFirst();
        } catch (ExternalServiceException | UnauthenticatedException e) {
            // The user:email scope may have been declined. Not fatal - we simply have no e-mail.
            log.debug("github_email_unavailable reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Repositories the token can access, sorted by most recently pushed.
     *
     * @param affiliation e.g. {@code owner,collaborator,organization_member}
     */
    public List<GitHubRepository> listRepositories(String token, int page, int perPage, String affiliation) {
        int size = Math.min(Math.max(perPage, 1), MAX_PER_PAGE);
        return call(
                "GET /user/repos",
                () ->
                        restClient
                                .get()
                                .uri(
                                        uriBuilder ->
                                                uriBuilder
                                                        .path("/user/repos")
                                                        .queryParam("per_page", size)
                                                        .queryParam("page", Math.max(page, 1))
                                                        .queryParam("sort", "pushed")
                                                        .queryParam("direction", "desc")
                                                        .queryParam("affiliation", affiliation)
                                                        .build())
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<GitHubRepository>>() {}));
    }

    public GitHubRepository getRepository(String token, String owner, String repo) {
        return call(
                "GET /repos/" + owner + "/" + repo,
                () ->
                        restClient
                                .get()
                                .uri("/repos/{owner}/{repo}", owner, repo)
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(GitHubRepository.class));
    }

    public List<GitHubBranch> listBranches(String token, String owner, String repo) {
        return call(
                "GET /repos/" + owner + "/" + repo + "/branches",
                () ->
                        restClient
                                .get()
                                .uri(
                                        uriBuilder ->
                                                uriBuilder
                                                        .path("/repos/{owner}/{repo}/branches")
                                                        .queryParam("per_page", MAX_PER_PAGE)
                                                        .build(owner, repo))
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<GitHubBranch>>() {}));
    }

    /** Latest commit on a ref (branch name, tag or sha). */
    public GitHubCommit getCommit(String token, String owner, String repo, String ref) {
        return call(
                "GET /repos/" + owner + "/" + repo + "/commits/" + ref,
                () ->
                        restClient
                                .get()
                                .uri("/repos/{owner}/{repo}/commits/{ref}", owner, repo, ref)
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(GitHubCommit.class));
    }

    /**
     * Reads a single file. Returns empty when the path does not exist, which is the normal case while
     * probing for {@code package.json}, {@code pom.xml}, {@code Dockerfile} and friends.
     */
    public Optional<String> getFileContent(
            String token, String owner, String repo, String path, String ref) {
        try {
            GitHubContent content =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/repos/{owner}/{repo}/contents/{path}")
                                                    .queryParam("ref", ref)
                                                    .build(owner, repo, path))
                            .headers(headers -> authorize(headers, token))
                            .retrieve()
                            .body(GitHubContent.class);
            if (content == null || content.content() == null) {
                return Optional.empty();
            }
            if (!"base64".equalsIgnoreCase(content.encoding())) {
                return Optional.of(content.content());
            }
            byte[] decoded = Base64.getMimeDecoder().decode(content.content());
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw translate("GET contents/" + path, e);
        }
    }

    /** Directory listing, used to detect files without downloading them. */
    public List<GitHubContent> listDirectory(
            String token, String owner, String repo, String path, String ref) {
        try {
            List<GitHubContent> contents =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/repos/{owner}/{repo}/contents/{path}")
                                                    .queryParam("ref", ref)
                                                    .build(owner, repo, path == null ? "" : path))
                            .headers(headers -> authorize(headers, token))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<GitHubContent>>() {});
            return contents == null ? List.of() : contents;
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw translate("GET contents listing", e);
        }
    }

    public List<GitHubHook> listWebhooks(String token, String owner, String repo) {
        return call(
                "GET hooks",
                () ->
                        restClient
                                .get()
                                .uri("/repos/{owner}/{repo}/hooks", owner, repo)
                                .headers(headers -> authorize(headers, token))
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<GitHubHook>>() {}));
    }

    public GitHubHook createWebhook(
            String token, String owner, String repo, String payloadUrl, String secret) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", payloadUrl);
        config.put("content_type", "json");
        config.put("secret", secret);
        config.put("insecure_ssl", "0");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", List.of("push", "pull_request"));
        body.put("config", config);

        return call(
                "POST hooks",
                () ->
                        restClient
                                .post()
                                .uri("/repos/{owner}/{repo}/hooks", owner, repo)
                                .headers(headers -> authorize(headers, token))
                                .body(body)
                                .retrieve()
                                .body(GitHubHook.class));
    }

    public void deleteWebhook(String token, String owner, String repo, Long hookId) {
        call(
                "DELETE hooks/" + hookId,
                () -> {
                    restClient
                            .delete()
                            .uri("/repos/{owner}/{repo}/hooks/{id}", owner, repo, hookId)
                            .headers(headers -> authorize(headers, token))
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                });
    }

    private void authorize(HttpHeaders headers, String token) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private <T> T call(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw translate(operation, e);
        }
    }

    /**
     * Maps GitHub failures onto DeployForge errors.
     *
     * <p>401/403-with-bad-credentials means the stored grant is no longer usable and the user must
     * reconnect - surfacing that as a generic 502 would send people debugging the wrong thing.
     */
    private RuntimeException translate(String operation, RuntimeException e) {
        if (e instanceof HttpClientErrorException clientError) {
            HttpStatusCode status = clientError.getStatusCode();
            String body = safeBody(clientError.getResponseBodyAsString());
            if (status.value() == 401) {
                log.warn("github_unauthorized operation={}", operation);
                return new UnauthenticatedException(
                        ErrorCode.GITHUB_NOT_CONNECTED,
                        "Your GitHub authorization is no longer valid. Reconnect GitHub to continue.");
            }
            if (status.value() == 404) {
                return new NotFoundException(
                        "GitHub returned 404 for this resource. It may be private, renamed or outside your grant.");
            }
            if (status.value() == 403 && body.contains("rate limit")) {
                log.warn("github_rate_limited operation={}", operation);
                return new ExternalServiceException(
                        ErrorCode.GITHUB_API_ERROR,
                        "GitHub API rate limit reached. Try again in a few minutes.");
            }
            if (status.value() == 403) {
                return new ExternalServiceException(
                        ErrorCode.GITHUB_API_ERROR,
                        "GitHub denied this request. The OAuth grant may be missing a required scope.");
            }
            log.warn("github_client_error operation={} status={} body={}", operation, status.value(), body);
            return new ExternalServiceException(
                    ErrorCode.GITHUB_API_ERROR, "GitHub rejected the request (" + status.value() + ")");
        }
        if (e instanceof ResourceAccessException) {
            log.warn("github_unreachable operation={} reason={}", operation, e.getMessage());
            return new ExternalServiceException(
                    ErrorCode.GITHUB_API_ERROR, "GitHub is unreachable right now. Try again shortly.", e);
        }
        log.warn("github_server_error operation={} reason={}", operation, e.getMessage());
        return new ExternalServiceException(
                ErrorCode.GITHUB_API_ERROR, "GitHub returned a server error. Try again shortly.", e);
    }

    private String safeBody(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.length() > 500 ? body.substring(0, 500) : body;
        return Arrays.stream(trimmed.split("\\R")).findFirst().orElse("");
    }
}
