package com.deployforge.project;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.common.api.PageResponse;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.util.Validators;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.environment.EnvironmentType;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.project.ProjectDeploymentSnapshotProvider.DeploymentSnapshot;
import com.deployforge.project.dto.ProjectDtos.BuildConfig;
import com.deployforge.project.dto.ProjectDtos.EnvironmentResponse;
import com.deployforge.project.dto.ProjectDtos.InitialVariable;
import com.deployforge.project.dto.ProjectDtos.ProjectResponse;
import com.deployforge.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.deployforge.project.dto.ProjectDtos.RepositoryRef;
import com.deployforge.project.dto.ProjectDtos.UpdateProjectRequest;
import com.deployforge.user.UserRepository;
import com.deployforge.workspace.AuthorizationService;
import com.deployforge.workspace.Permission;
import com.deployforge.workspace.WorkspaceRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Project reads, configuration updates and deletion.
 *
 * <p>Transactions are declared per method rather than on the class: import and delete deliberately
 * run <em>outside</em> a transaction because they call GitHub and the Docker engine, and holding a
 * database connection across those would be a mistake. The GitHub-facing orchestration lives in
 * {@link ProjectImportService}.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableService variableService;
    private final AuthorizationService authorization;
    private final ProjectAccessGuard accessGuard;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final ObjectProvider<ProjectDeploymentSnapshotProvider> snapshotProvider;
    private final ObjectProvider<ProjectResourceReclaimer> resourceReclaimer;

    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            EnvironmentVariableService variableService,
            AuthorizationService authorization,
            ProjectAccessGuard accessGuard,
            UserRepository userRepository,
            ActivityService activityService,
            ObjectProvider<ProjectDeploymentSnapshotProvider> snapshotProvider,
            ObjectProvider<ProjectResourceReclaimer> resourceReclaimer) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.variableService = variableService;
        this.authorization = authorization;
        this.accessGuard = accessGuard;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.snapshotProvider = snapshotProvider;
        this.resourceReclaimer = resourceReclaimer;
    }

    // ------------------------------------------------------------------ writes

    /**
     * Persists an imported project together with its production environment and initial variables.
     *
     * <p>Called by {@link ProjectImportService} after all GitHub work is done, so this transaction is
     * short.
     */
    @Transactional
    public Project persistImport(
            Project project, String branch, boolean autoDeployEnabled, List<InitialVariable> variables) {
        Project saved = projectRepository.save(project);
        Environment environment =
                environmentRepository.save(
                        new Environment(
                                saved.getId(),
                                "production",
                                EnvironmentType.PRODUCTION,
                                branch,
                                autoDeployEnabled));
        if (variables != null) {
            for (InitialVariable variable : variables) {
                variableService.upsert(environment.getId(), variable.key(), variable.value());
            }
        }
        return saved;
    }

    @Transactional
    public void applyUpdate(UUID projectId, UUID userId, UpdateProjectRequest request) {
        Project project = accessGuard.require(projectId, userId, Permission.MANAGE_PROJECT);

        if (StringUtils.hasText(request.name())) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        if (request.framework() != null && request.framework() != project.getFramework()) {
            project.setFramework(request.framework());
            project.setRuntime(request.framework().runtime());
        }
        if (request.buildConfig() != null) {
            applyBuildConfig(project, request.buildConfig());
        }

        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                userId,
                actorName(userId),
                ActivityAction.PROJECT_UPDATED,
                "PROJECT",
                project.getId().toString(),
                Map.of());
    }

    public ProjectResponse update(UUID projectId, UUID userId, UpdateProjectRequest request) {
        applyUpdate(projectId, userId, request);
        return get(projectId, userId);
    }

    private void applyBuildConfig(Project project, BuildConfig config) {
        if (config.rootDirectory() != null) {
            project.setRootDirectory(
                    Validators.normalizeRelativePath(config.rootDirectory(), "Root directory"));
        }
        if (config.installCommand() != null) {
            project.setInstallCommand(
                    Validators.requireCommand(config.installCommand(), "Install command"));
        }
        if (config.buildCommand() != null) {
            project.setBuildCommand(Validators.requireCommand(config.buildCommand(), "Build command"));
        }
        if (config.startCommand() != null) {
            project.setStartCommand(Validators.requireCommand(config.startCommand(), "Start command"));
        }
        if (config.dockerfilePath() != null) {
            project.setDockerfilePath(
                    Validators.normalizeRelativePath(config.dockerfilePath(), "Dockerfile path"));
        }
        if (config.port() != null) {
            project.setContainerPort(Validators.requirePort(config.port(), "Port"));
        }
        if (config.healthCheckPath() != null) {
            project.setHealthCheckPath(Validators.requireHealthPath(config.healthCheckPath()));
        }
    }

    /**
     * Deletes a project.
     *
     * <p>Runs without an enclosing transaction on purpose: infrastructure is released first
     * (containers stopped, images and build directories removed) because the container ids needed for
     * that cleanup live in rows the delete is about to cascade away.
     */
    public void delete(UUID projectId, UUID userId) {
        Project project = accessGuard.require(projectId, userId, Permission.DELETE_PROJECT);
        UUID workspaceId = project.getWorkspaceId();
        String name = project.getName();

        resourceReclaimer.ifAvailable(reclaimer -> reclaimer.reclaim(projectId));
        projectRepository.deleteById(projectId);

        log.info("project_deleted project={} by={}", projectId, userId);
        activityService.record(
                workspaceId,
                null,
                userId,
                actorName(userId),
                ActivityAction.PROJECT_DELETED,
                "PROJECT",
                projectId.toString(),
                Map.of("name", name));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> list(UUID userId, UUID workspaceId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Project> projects;
        if (workspaceId != null) {
            authorization.requireMembership(workspaceId, userId);
            projects = projectRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageRequest);
        } else {
            projects = projectRepository.findAllVisibleTo(userId, pageRequest);
        }

        List<UUID> ids = projects.getContent().stream().map(Project::getId).toList();
        Map<UUID, DeploymentSnapshot> snapshots = latestDeployments(ids);
        Map<UUID, String> productionBranches = productionBranches(ids);

        return PageResponse.from(
                projects,
                project ->
                        new ProjectSummaryResponse(
                                project.getId(),
                                project.getWorkspaceId(),
                                project.getName(),
                                project.getSlug(),
                                repositoryRef(project),
                                project.getFramework(),
                                project.getFramework() == null ? null : project.getFramework().displayName(),
                                project.getStatus(),
                                productionBranches.get(project.getId()),
                                snapshots.get(project.getId()),
                                project.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId, UUID userId) {
        Project project = accessGuard.requireVisible(projectId, userId);
        WorkspaceRole role = accessGuard.roleFor(project, userId);
        List<EnvironmentResponse> environments =
                environmentRepository.findByProjectIdOrderByTypeAscNameAsc(projectId).stream()
                        .map(this::toEnvironmentResponse)
                        .toList();

        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                repositoryRef(project),
                project.getDefaultBranch(),
                project.getFramework(),
                project.getFramework() == null ? null : project.getFramework().displayName(),
                project.getRuntime(),
                project.getStatus(),
                buildConfigOf(project),
                environments,
                latestDeployments(List.of(projectId)).get(projectId),
                role.permissions(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Project requireProject(UUID projectId) {
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }

    @Transactional(readOnly = true)
    public List<Environment> environmentsOf(UUID projectId) {
        return environmentRepository.findByProjectIdOrderByTypeAscNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Environment> productionEnvironment(UUID projectId) {
        return environmentRepository.findFirstByProjectIdAndType(projectId, EnvironmentType.PRODUCTION);
    }

    @Transactional(readOnly = true)
    public List<Project> allInWorkspace(UUID workspaceId) {
        return new ArrayList<>(projectRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    public EnvironmentResponse toEnvironmentResponse(Environment environment) {
        return new EnvironmentResponse(
                environment.getId(),
                environment.getProjectId(),
                environment.getName(),
                environment.getType(),
                environment.getBranch(),
                environment.isAutoDeployEnabled(),
                variableService.count(environment.getId()),
                environment.getCreatedAt());
    }

    public RepositoryRef repositoryRef(Project project) {
        return new RepositoryRef(
                project.getRepositoryOwner(),
                project.getRepositoryName(),
                project.repositoryFullName(),
                project.getRepositoryUrl(),
                project.isRepositoryPrivate(),
                project.getRepositoryId());
    }

    public BuildConfig buildConfigOf(Project project) {
        return new BuildConfig(
                project.getRootDirectory(),
                project.getInstallCommand(),
                project.getBuildCommand(),
                project.getStartCommand(),
                project.getDockerfilePath(),
                project.getContainerPort(),
                project.getHealthCheckPath());
    }

    public String actorName(UUID userId) {
        return userRepository.findById(userId).map(user -> user.displayName()).orElse("unknown");
    }

    private Map<UUID, DeploymentSnapshot> latestDeployments(Collection<UUID> projectIds) {
        ProjectDeploymentSnapshotProvider provider = snapshotProvider.getIfAvailable();
        if (provider == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return provider.latestByProject(projectIds);
    }

    private Map<UUID, String> productionBranches(List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> branches = new HashMap<>();
        for (Environment environment : environmentRepository.findByProjectIdIn(projectIds)) {
            if (environment.getType() == EnvironmentType.PRODUCTION) {
                branches.put(environment.getProjectId(), environment.getBranch());
            } else {
                branches.putIfAbsent(environment.getProjectId(), environment.getBranch());
            }
        }
        return branches;
    }
}
