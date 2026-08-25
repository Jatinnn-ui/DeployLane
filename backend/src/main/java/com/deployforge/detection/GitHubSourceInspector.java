package com.deployforge.detection;

import com.deployforge.github.GitHubService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inspects a repository through the GitHub API, used on the import screen before anything is cloned.
 *
 * <p>Results are memoised per instance: detection probes the same handful of files repeatedly
 * (package.json, lockfiles, Dockerfile) and each miss would otherwise cost an API call.
 */
public class GitHubSourceInspector implements SourceInspector {

    private final GitHubService githubService;
    private final UUID userId;
    private final String owner;
    private final String repo;
    private final String ref;

    private final Map<String, Optional<String>> fileCache = new HashMap<>();
    private final Map<String, List<String>> listingCache = new HashMap<>();

    public GitHubSourceInspector(
            GitHubService githubService, UUID userId, String owner, String repo, String ref) {
        this.githubService = githubService;
        this.userId = userId;
        this.owner = owner;
        this.repo = repo;
        this.ref = ref;
    }

    @Override
    public Optional<String> readFile(String relativePath) {
        return fileCache.computeIfAbsent(
                normalize(relativePath),
                path -> githubService.readFile(userId, owner, repo, path, ref));
    }

    @Override
    public boolean exists(String relativePath) {
        String normalized = normalize(relativePath);
        int lastSlash = normalized.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        String name = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        return listDirectory(directory).stream().anyMatch(entry -> entry.equalsIgnoreCase(name));
    }

    @Override
    public List<String> listDirectory(String relativeDirectory) {
        return listingCache.computeIfAbsent(
                normalize(relativeDirectory),
                path ->
                        path.isEmpty()
                                ? githubService.listRootEntries(userId, owner, repo, ref)
                                : githubService.listDirectoryEntries(userId, owner, repo, path, ref));
    }

    @Override
    public String describe() {
        return owner + "/" + repo + "@" + ref;
    }

    private String normalize(String path) {
        if (path == null) {
            return "";
        }
        String cleaned = path.replace('\\', '/');
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}
