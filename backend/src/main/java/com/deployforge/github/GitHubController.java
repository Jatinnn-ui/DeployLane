package com.deployforge.github;

import com.deployforge.github.dto.GitHubDtos.BranchSummary;
import com.deployforge.github.dto.GitHubDtos.CommitSummary;
import com.deployforge.github.dto.GitHubDtos.GitHubConnectionStatus;
import com.deployforge.github.dto.GitHubDtos.RepositoryListResponse;
import com.deployforge.github.dto.GitHubDtos.RepositorySummary;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only GitHub browsing used by the project import flow. */
@RestController
@RequestMapping("/api/v1/github")
@Validated
@Tag(name = "GitHub", description = "Repository, branch and commit browsing on behalf of the user")
public class GitHubController {

    private final GitHubService githubService;

    public GitHubController(GitHubService githubService) {
        this.githubService = githubService;
    }

    @GetMapping("/connection")
    @Operation(summary = "GitHub connection state and granted scopes")
    public GitHubConnectionStatus connection(@AuthenticationPrincipal AuthenticatedUser user) {
        return githubService.connectionStatus(user.userId());
    }

    @GetMapping("/repositories")
    @Operation(
            summary = "List accessible repositories",
            description =
                    "Searchable and paginated. Results come from a short lived per-user snapshot so typing in "
                            + "the picker does not exhaust the GitHub API rate limit.")
    public RepositoryListResponse repositories(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "perPage", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        return githubService.listRepositories(user.userId(), query, page, perPage);
    }

    @GetMapping("/repositories/{owner}/{repo}")
    public RepositorySummary repository(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String owner,
            @PathVariable String repo) {
        return githubService.getRepository(user.userId(), owner, repo);
    }

    @GetMapping("/repositories/{owner}/{repo}/branches")
    @Operation(summary = "Branches of a repository")
    public List<BranchSummary> branches(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String owner,
            @PathVariable String repo) {
        return githubService.listBranches(user.userId(), owner, repo);
    }

    @GetMapping("/repositories/{owner}/{repo}/commits/latest")
    @Operation(summary = "Latest commit on a branch")
    public CommitSummary latestCommit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(name = "ref") String ref) {
        return githubService.getLatestCommit(user.userId(), owner, repo, ref);
    }
}
