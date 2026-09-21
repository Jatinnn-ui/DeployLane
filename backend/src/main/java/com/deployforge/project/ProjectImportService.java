package com.deployforge.project;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.ConflictException;
import com.deployforge.common.util.Slugs;
import com.deployforge.common.util.Validators;
import com.deployforge.detection.Framework;
import com.deployforge.detection.FrameworkDetectionResult;
import com.deployforge.detection.FrameworkDetectionService;
import com.deployforge.detection.GitHubSourceInspector;
import com.deployforge.detection.RuntimeType;
import com.deployforge.github.GitHubService;
import com.deployforge.github.dto.GitHubDtos.BranchSummary;
import com.deployforge.github.dto.GitHubDtos.RepositorySummary;
import com.deployforge.project.dto.ProjectDtos.BuildConfig;
import com.deployforge.project.dto.ProjectDtos.ImportProjectRequest;
import com.deployforge.project.dto.ProjectDtos.ProjectResponse;
import com.deployforge.workspace.AuthorizationService;
import com.deployforge.workspace.Permission;
import com.deployforge.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Imports a GitHub repository as a project.
 *
 * <p>Ordering matters here: authorization, then GitHub verification, then framework detection, and
 * only then a single short write transaction. No network call happens while a database transaction is
 * open.
 */
@Service
public class ProjectImportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportService.class);

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final GitHubService githubService;
    private final FrameworkDetectionService detectionService;
    private final AuthorizationService authorization;
    private final WorkspaceRepository workspaceRepository;
    private final ActivityService activityService;
    private final com.deployforge.auth.DeployAllowlistGuard deployAllowlistGuard;

    public ProjectImportService(
            ProjectService projectService,
            ProjectRepository projectRepository,
            GitHubService githubService,
            FrameworkDetectionService detectionService,
            AuthorizationService authorization,
            WorkspaceRepository workspaceRepository,
            ActivityService activityService,
            com.deployforge.auth.DeployAllowlistGuard deployAllowlistGuard) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.githubService = githubService;
        this.detectionService = detectionService;
        this.authorization = authorization;
        this.workspaceRepository = workspaceRepository;
        this.activityService = activityService;
        this.deployAllowlistGuard = deployAllowlistGuard;
    }

    public ProjectResponse importProject(UUID userId, ImportProjectRequest request) {
        UUID workspaceId = resolveWorkspace(userId, request.workspaceId());
        authorization.require(workspaceId, userId, Permission.MANAGE_PROJECT);
        deployAllowlistGuard.requireCanDeploy(userId);

        String owner = Validators.requireGithubName(request.repositoryOwner(), "Repository owner");
        String repo = Validators.requireGithubName(request.repositoryName(), "Repository name");
        String branch = Validators.requireBranch(request.branch());

        RepositorySummary repository = githubService.requireRepository(userId, owner, repo);
        if (repository.archived()) {
            throw new BadRequestException(
                    "This repository is archived on GitHub and cannot be deployed. Unarchive it first.");
        }
        List<BranchSummary> branches = githubService.listBranches(userId, owner, repo);
        if (branches.stream().noneMatch(candidate -> candidate.name().equals(branch))) {
            throw new BadRequestException(
                    "Branch '" + branch + "' does not exist in " + repository.fullName());
        }

        BuildConfig requested =
                request.buildConfig() == null
                        ? new BuildConfig(null, null, null, null, null, null, null)
                        : request.buildConfig();
        String rootDirectory =
                Validators.normalizeRelativePath(requested.rootDirectory(), "Root directory");

        FrameworkDetectionResult detection =
                detectionService.detect(
                        new GitHubSourceInspector(githubService, userId, owner, repo, branch), rootDirectory);

        Framework framework = request.framework() != null ? request.framework() : detection.framework();
        if (framework == Framework.UNKNOWN) {
            throw new BadRequestException(
                    "DeployLane could not determine how to build this repository. Pick a framework explicitly, or add a Dockerfile.");
        }

        String projectName =
                StringUtils.hasText(request.name()) ? request.name().trim() : repository.name();
        String slug =
                uniqueSlug(
                        workspaceId,
                        StringUtils.hasText(request.slug()) ? request.slug() : Slugs.slugify(projectName));

        Project project =
                new Project(
                        workspaceId,
                        projectName,
                        slug,
                        owner,
                        repo,
                        repository.htmlUrl(),
                        repository.id(),
                        repository.isPrivate(),
                        repository.defaultBranch() == null ? branch : repository.defaultBranch(),
                        userId);
        project.setDescription(
                StringUtils.hasText(request.description())
                        ? request.description().trim()
                        : repository.description());
        applyDetectedConfiguration(project, framework, detection, requested, rootDirectory);

        Project saved =
                projectService.persistImport(
                        project, branch, request.autoDeployEnabled(), request.environmentVariables());

        log.info(
                "project_imported project={} repository={} framework={} branch={} confidence={}",
                saved.getId(),
                repository.fullName(),
                framework,
                branch,
                detection.confidence());

        activityService.record(
                workspaceId,
                saved.getId(),
                userId,
                projectService.actorName(userId),
                ActivityAction.PROJECT_IMPORTED,
                "PROJECT",
                saved.getId().toString(),
                Map.of(
                        "repository",
                        repository.fullName(),
                        "branch",
                        branch,
                        "framework",
                        framework.name()));

        return projectService.get(saved.getId(), userId);
    }

    /** Detection supplies defaults; anything the user typed wins. */
    private void applyDetectedConfiguration(
            Project project,
            Framework framework,
            FrameworkDetectionResult detection,
            BuildConfig requested,
            String rootDirectory) {

        project.setFramework(framework);
        project.setRuntime(
                framework == detection.framework() && detection.runtime() != RuntimeType.UNKNOWN
                        ? detection.runtime()
                        : framework.runtime());
        project.setRootDirectory(rootDirectory);

        project.setInstallCommand(
                Validators.requireCommand(
                        firstNonBlank(requested.installCommand(), detection.installCommand()),
                        "Install command"));
        project.setBuildCommand(
                Validators.requireCommand(
                        firstNonBlank(requested.buildCommand(), detection.buildCommand()), "Build command"));
        project.setStartCommand(
                Validators.requireCommand(
                        firstNonBlank(requested.startCommand(), detection.startCommand()), "Start command"));
        project.setDockerfilePath(
                Validators.normalizeRelativePath(
                        firstNonBlank(requested.dockerfilePath(), detection.dockerfilePath()),
                        "Dockerfile path"));
        project.setContainerPort(
                Validators.requirePort(
                        requested.port() != null ? requested.port() : detection.port(), "Port"));
        project.setHealthCheckPath(Validators.requireHealthPath(requested.healthCheckPath()));
    }

    private UUID resolveWorkspace(UUID userId, UUID requested) {
        if (requested != null) {
            return requested;
        }
        return workspaceRepository.findAllForMember(userId).stream()
                .findFirst()
                .map(workspace -> workspace.getId())
                .orElseThrow(
                        () ->
                                new BadRequestException(
                                        "You do not belong to any workspace yet. Create one before importing a project."));
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String uniqueSlug(UUID workspaceId, String base) {
        String slug = Slugs.slugify(base);
        if (slug.isEmpty()) {
            throw new BadRequestException(
                    "Project name must contain at least one alphanumeric character");
        }
        if (!projectRepository.existsByWorkspaceIdAndSlug(workspaceId, slug)) {
            return slug;
        }
        for (int attempt = 2; attempt <= 50; attempt++) {
            String candidate = slug + "-" + attempt;
            if (!projectRepository.existsByWorkspaceIdAndSlug(workspaceId, candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Too many projects already use the slug '" + slug + "'");
    }
}
