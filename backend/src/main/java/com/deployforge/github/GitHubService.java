package com.deployforge.github;

import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.error.Exceptions.UnauthenticatedException;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.common.util.Validators;
import com.deployforge.github.api.GitHubApiClient;
import com.deployforge.github.api.GitHubApiModels.GitHubRepository;
import com.deployforge.github.dto.GitHubDtos.BranchSummary;
import com.deployforge.github.dto.GitHubDtos.CommitSummary;
import com.deployforge.github.dto.GitHubDtos.GitHubConnectionStatus;
import com.deployforge.github.dto.GitHubDtos.RepositoryListResponse;
import com.deployforge.github.dto.GitHubDtos.RepositorySummary;
import com.deployforge.security.EncryptionService;
import com.deployforge.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Everything the platform needs from GitHub, on behalf of a specific user.
 *
 * <p>The repository catalogue is cached in Redis for a short window. Repository pickers are typed
 * into, so without a cache every keystroke-driven refetch would burn GitHub API quota; with it,
 * search and sort happen locally over a recent snapshot.
 */
@Service
@Transactional(readOnly = true)
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    private static final String CACHE_PREFIX = "df:gh:repos:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(120);
    private static final int CATALOGUE_PAGES = 3;
    private static final int CATALOGUE_PAGE_SIZE = 100;

    private final GitHubConnectionRepository connectionRepository;
    private final GitHubApiClient apiClient;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final JsonCodec json;

    public GitHubService(
            GitHubConnectionRepository connectionRepository,
            GitHubApiClient apiClient,
            EncryptionService encryptionService,
            UserRepository userRepository,
            StringRedisTemplate redis,
            JsonCodec json) {
        this.connectionRepository = connectionRepository;
        this.apiClient = apiClient;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
        this.redis = redis;
        this.json = json;
    }

    // ------------------------------------------------------------------ connection

    @Transactional
    public GitHubConnection saveConnection(
            UUID userId, Long githubAccountId, String accessToken, String tokenType, String scopes) {
        String encrypted = encryptionService.encrypt(accessToken);
        GitHubConnection connection =
                connectionRepository
                        .findByUserId(userId)
                        .map(
                                existing -> {
                                    existing.setAccessTokenEncrypted(encrypted);
                                    existing.setTokenType(tokenType);
                                    existing.setScopes(scopes);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        new GitHubConnection(
                                                userId, githubAccountId, encrypted, tokenType, scopes));
        return connectionRepository.save(connection);
    }

    public GitHubConnectionStatus connectionStatus(UUID userId) {
        return connectionRepository
                .findByUserId(userId)
                .map(
                        connection ->
                                new GitHubConnectionStatus(
                                        true,
                                        userRepository
                                                .findById(userId)
                                                .map(user -> user.getGithubUsername())
                                                .orElse(null),
                                        connection.getScopes(),
                                        connection.hasRepoScope(),
                                        connection.getUpdatedAt()))
                .orElseGet(GitHubConnectionStatus::disconnected);
    }

    /**
     * Decrypted access token for the user.
     *
     * @throws UnauthenticatedException with {@code GITHUB_NOT_CONNECTED} when there is no grant, so
     *     the frontend can prompt for reconnection instead of showing a generic failure
     */
    public String requireAccessToken(UUID userId) {
        GitHubConnection connection =
                connectionRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new UnauthenticatedException(
                                                ErrorCode.GITHUB_NOT_CONNECTED,
                                                "GitHub is not connected for this account. Sign in with GitHub again to reconnect."));
        return encryptionService.decrypt(connection.getAccessTokenEncrypted());
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.deleteByUserId(userId);
        evictCatalogue(userId);
    }

    // ------------------------------------------------------------------ repositories

    /**
     * Searchable, paginated repository list.
     *
     * @param query optional case insensitive filter matched against the repository full name and
     *     description
     */
    public RepositoryListResponse listRepositories(UUID userId, String query, int page, int perPage) {
        List<RepositorySummary> catalogue = repositoryCatalogue(userId);

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<RepositorySummary> filtered =
                needle.isEmpty()
                        ? catalogue
                        : catalogue.stream()
                                .filter(
                                        repository ->
                                                matches(repository.fullName(), needle)
                                                        || matches(repository.description(), needle)
                                                        || matches(repository.language(), needle))
                                .toList();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(perPage, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());

        return new RepositoryListResponse(
                filtered.subList(from, to), safePage, safeSize, to < filtered.size(), filtered.size());
    }

    public RepositorySummary getRepository(UUID userId, String owner, String repo) {
        Validators.requireGithubName(owner, "Repository owner");
        Validators.requireGithubName(repo, "Repository name");
        return RepositorySummary.from(apiClient.getRepository(requireAccessToken(userId), owner, repo));
    }

    public List<BranchSummary> listBranches(UUID userId, String owner, String repo) {
        Validators.requireGithubName(owner, "Repository owner");
        Validators.requireGithubName(repo, "Repository name");
        List<com.deployforge.github.api.GitHubApiModels.GitHubBranch> branches =
                apiClient.listBranches(requireAccessToken(userId), owner, repo);
        if (branches == null) {
            return List.of();
        }
        return branches.stream()
                .map(BranchSummary::from)
                .sorted(Comparator.comparing(BranchSummary::name))
                .toList();
    }

    public CommitSummary getLatestCommit(UUID userId, String owner, String repo, String ref) {
        Validators.requireGithubName(owner, "Repository owner");
        Validators.requireGithubName(repo, "Repository name");
        Validators.requireBranch(ref);
        return CommitSummary.from(apiClient.getCommit(requireAccessToken(userId), owner, repo, ref));
    }

    /** Used by framework detection to inspect a repository without cloning it. */
    public Optional<String> readFile(
            UUID userId, String owner, String repo, String path, String ref) {
        return apiClient.getFileContent(requireAccessToken(userId), owner, repo, path, ref);
    }

    public List<String> listRootEntries(UUID userId, String owner, String repo, String ref) {
        return listDirectoryEntries(userId, owner, repo, "", ref);
    }

    public List<String> listDirectoryEntries(
            UUID userId, String owner, String repo, String path, String ref) {
        return apiClient.listDirectory(requireAccessToken(userId), owner, repo, path, ref).stream()
                .map(content -> content.name())
                .toList();
    }

    // ------------------------------------------------------------------ webhooks

    public List<com.deployforge.github.api.GitHubApiModels.GitHubHook> listWebhooks(
            UUID userId, String owner, String repo) {
        return apiClient.listWebhooks(requireAccessToken(userId), owner, repo);
    }

    public com.deployforge.github.api.GitHubApiModels.GitHubHook createWebhook(
            UUID userId, String owner, String repo, String payloadUrl, String secret) {
        return apiClient.createWebhook(requireAccessToken(userId), owner, repo, payloadUrl, secret);
    }

    public void deleteWebhook(UUID userId, String owner, String repo, Long hookId) {
        apiClient.deleteWebhook(requireAccessToken(userId), owner, repo, hookId);
    }

    // ------------------------------------------------------------------ internals

    private boolean matches(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Snapshot of the user's repositories, cached briefly in Redis.
     *
     * <p>Cache misses are fetched page by page and stop early once GitHub returns a short page.
     */
    private List<RepositorySummary> repositoryCatalogue(UUID userId) {
        String cacheKey = CACHE_PREFIX + userId;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                List<RepositorySummary> parsed =
                        json.read(cached, new TypeReference<List<RepositorySummary>>() {}, null);
                if (parsed != null) {
                    return parsed;
                }
            }
        } catch (DataAccessException e) {
            log.debug("github_catalogue_cache_unavailable reason={}", e.getMessage());
        }

        String token = requireAccessToken(userId);
        List<RepositorySummary> all = new java.util.ArrayList<>();
        for (int page = 1; page <= CATALOGUE_PAGES; page++) {
            List<GitHubRepository> batch =
                    apiClient.listRepositories(
                            token, page, CATALOGUE_PAGE_SIZE, "owner,collaborator,organization_member");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            batch.stream().map(RepositorySummary::from).forEach(all::add);
            if (batch.size() < CATALOGUE_PAGE_SIZE) {
                break;
            }
        }

        try {
            redis.opsForValue().set(cacheKey, json.write(all), CACHE_TTL);
        } catch (DataAccessException e) {
            log.debug("github_catalogue_cache_write_failed reason={}", e.getMessage());
        }
        return all;
    }

    public void evictCatalogue(UUID userId) {
        try {
            redis.delete(CACHE_PREFIX + userId);
        } catch (DataAccessException e) {
            log.debug("github_catalogue_evict_failed reason={}", e.getMessage());
        }
    }

    /** Convenience for services that only have an owner/name pair. */
    public RepositorySummary requireRepository(UUID userId, String owner, String repo) {
        RepositorySummary summary = getRepository(userId, owner, repo);
        if (summary == null) {
            throw new NotFoundException("Repository " + owner + "/" + repo + " was not found");
        }
        return summary;
    }
}
