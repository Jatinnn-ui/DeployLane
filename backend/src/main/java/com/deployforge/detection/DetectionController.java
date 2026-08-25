package com.deployforge.detection;

import com.deployforge.common.util.Validators;
import com.deployforge.github.GitHubService;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Framework detection for the import screen.
 *
 * <p>Runs against the GitHub API, so the user sees the detected framework, install/build/start
 * commands and port before the project exists - and can override any of them.
 */
@RestController
@RequestMapping("/api/v1/detection")
@Tag(name = "Detection", description = "Framework and build configuration detection")
public class DetectionController {

    private final FrameworkDetectionService detectionService;
    private final GitHubService githubService;

    public DetectionController(
            FrameworkDetectionService detectionService, GitHubService githubService) {
        this.detectionService = detectionService;
        this.githubService = githubService;
    }

    @PostMapping
    @Operation(summary = "Detect the framework of a repository branch")
    public FrameworkDetectionResult detect(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DetectRequest request) {
        String owner = Validators.requireGithubName(request.owner(), "Repository owner");
        String repo = Validators.requireGithubName(request.repo(), "Repository name");
        String ref = Validators.requireBranch(request.ref());
        String rootDirectory =
                Validators.normalizeRelativePath(request.rootDirectory(), "Root directory");

        return detectionService.detect(
                new GitHubSourceInspector(githubService, user.userId(), owner, repo, ref), rootDirectory);
    }

    public record DetectRequest(
            @NotBlank @Size(max = 100) String owner,
            @NotBlank @Size(max = 100) String repo,
            @NotBlank @Size(max = 255) String ref,
            @Size(max = 512) String rootDirectory) {}
}
