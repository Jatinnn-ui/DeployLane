package com.deployforge.environment;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.ConflictException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.util.Slugs;
import com.deployforge.common.util.Validators;
import com.deployforge.environment.dto.EnvironmentVariableDtos.BulkVariablesResponse;
import com.deployforge.environment.dto.EnvironmentVariableDtos.EnvironmentVariableResponse;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.project.ProjectService;
import com.deployforge.project.dto.ProjectDtos.CreateEnvironmentRequest;
import com.deployforge.project.dto.ProjectDtos.EnvironmentResponse;
import com.deployforge.project.dto.ProjectDtos.UpdateEnvironmentRequest;
import com.deployforge.workspace.Permission;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Environment management and the authorization entry point for environment scoped operations.
 *
 * <p>Variable endpoints are addressed by environment id or variable id, never by project id, so this
 * service resolves the owning project before delegating any permission decision to
 * {@link ProjectAccessGuard}.
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);
    private static final int MAX_ENVIRONMENTS_PER_PROJECT = 10;

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableRepository variableRepository;
    private final EnvironmentVariableService variableService;
    private final ProjectAccessGuard accessGuard;
    private final ProjectService projectService;
    private final ActivityService activityService;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            EnvironmentVariableRepository variableRepository,
            EnvironmentVariableService variableService,
            ProjectAccessGuard accessGuard,
            ProjectService projectService,
            ActivityService activityService) {
        this.environmentRepository = environmentRepository;
        this.variableRepository = variableRepository;
        this.variableService = variableService;
        this.accessGuard = accessGuard;
        this.projectService = projectService;
        this.activityService = activityService;
    }

    // ------------------------------------------------------------------ environments

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list(UUID projectId, UUID userId) {
        accessGuard.requireVisible(projectId, userId);
        return environmentRepository.findByProjectIdOrderByTypeAscNameAsc(projectId).stream()
                .map(projectService::toEnvironmentResponse)
                .toList();
    }

    @Transactional
    public EnvironmentResponse create(UUID projectId, UUID userId, CreateEnvironmentRequest request) {
        Project project = accessGuard.require(projectId, userId, Permission.MANAGE_ENVIRONMENT);

        String name = Slugs.slugify(request.name());
        if (name.isEmpty()) {
            throw new BadRequestException("Environment name must contain alphanumeric characters");
        }
        if (environmentRepository.findByProjectIdAndName(projectId, name).isPresent()) {
            throw new ConflictException("An environment named '" + name + "' already exists");
        }
        if (environmentRepository.findByProjectIdOrderByTypeAscNameAsc(projectId).size()
                >= MAX_ENVIRONMENTS_PER_PROJECT) {
            throw new ConflictException(
                    "A project may have at most " + MAX_ENVIRONMENTS_PER_PROJECT + " environments");
        }

        EnvironmentType type = request.type() == null ? EnvironmentType.DEVELOPMENT : request.type();
        if (type == EnvironmentType.PRODUCTION
                && environmentRepository.findFirstByProjectIdAndType(projectId, EnvironmentType.PRODUCTION).isPresent()) {
            throw new ConflictException("This project already has a production environment");
        }

        Environment environment =
                environmentRepository.save(
                        new Environment(
                                projectId,
                                name,
                                type,
                                Validators.requireBranch(request.branch()),
                                request.autoDeployEnabled()));

        activityService.record(
                project.getWorkspaceId(),
                projectId,
                userId,
                projectService.actorName(userId),
                ActivityAction.ENVIRONMENT_CREATED,
                "ENVIRONMENT",
                environment.getId().toString(),
                Map.of("name", name, "branch", environment.getBranch()));

        return projectService.toEnvironmentResponse(environment);
    }

    @Transactional
    public EnvironmentResponse update(UUID environmentId, UUID userId, UpdateEnvironmentRequest request) {
        Environment environment = requireEnvironment(environmentId);
        Project project = accessGuard.require(environment.getProjectId(), userId, Permission.MANAGE_ENVIRONMENT);

        if (StringUtils.hasText(request.branch())) {
            environment.setBranch(Validators.requireBranch(request.branch()));
        }
        if (request.autoDeployEnabled() != null) {
            environment.setAutoDeployEnabled(request.autoDeployEnabled());
        }

        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                userId,
                projectService.actorName(userId),
                ActivityAction.ENVIRONMENT_UPDATED,
                "ENVIRONMENT",
                environmentId.toString(),
                Map.of(
                        "branch", environment.getBranch(),
                        "autoDeploy", environment.isAutoDeployEnabled()));

        return projectService.toEnvironmentResponse(environment);
    }

    @Transactional
    public void delete(UUID environmentId, UUID userId) {
        Environment environment = requireEnvironment(environmentId);
        Project project = accessGuard.require(environment.getProjectId(), userId, Permission.MANAGE_ENVIRONMENT);
        if (environment.getType() == EnvironmentType.PRODUCTION) {
            throw new BadRequestException(
                    "The production environment cannot be deleted. Archive the project instead.");
        }
        environmentRepository.delete(environment);
        log.info("environment_deleted environment={} project={}", environmentId, project.getId());
        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                userId,
                projectService.actorName(userId),
                ActivityAction.ENVIRONMENT_DELETED,
                "ENVIRONMENT",
                environmentId.toString(),
                Map.of("name", environment.getName()));
    }

    @Transactional(readOnly = true)
    public Environment requireEnvironment(UUID environmentId) {
        return environmentRepository
                .findById(environmentId)
                .orElseThrow(() -> NotFoundException.of("Environment", environmentId));
    }

    /** Resolves an environment and authorizes the caller against its project in one step. */
    @Transactional(readOnly = true)
    public Environment requireEnvironmentAccess(UUID environmentId, UUID userId, Permission permission) {
        Environment environment = requireEnvironment(environmentId);
        accessGuard.require(environment.getProjectId(), userId, permission);
        return environment;
    }

    // ------------------------------------------------------------------ variables

    @Transactional(readOnly = true)
    public List<EnvironmentVariableResponse> listVariables(UUID environmentId, UUID userId) {
        Environment environment = requireEnvironment(environmentId);
        accessGuard.requireVisible(environment.getProjectId(), userId);
        return variableService.list(environmentId);
    }

    @Transactional
    public EnvironmentVariableResponse createVariable(
            UUID environmentId, UUID userId, String key, String value) {
        Environment environment =
                requireEnvironmentAccess(environmentId, userId, Permission.MANAGE_VARIABLES);
        EnvironmentVariableResponse created = variableService.create(environmentId, key, value);
        recordVariableActivity(
                environment, userId, ActivityAction.VARIABLE_CREATED, Map.of("key", created.key()));
        return created;
    }

    @Transactional
    public EnvironmentVariableResponse updateVariable(UUID variableId, UUID userId, String value) {
        EnvironmentVariable variable =
                variableRepository
                        .findById(variableId)
                        .orElseThrow(() -> NotFoundException.of("Environment variable", variableId));
        Environment environment =
                requireEnvironmentAccess(variable.getEnvironmentId(), userId, Permission.MANAGE_VARIABLES);
        EnvironmentVariableResponse updated = variableService.update(variableId, value);
        recordVariableActivity(
                environment, userId, ActivityAction.VARIABLE_UPDATED, Map.of("key", variable.getKey()));
        return updated;
    }

    @Transactional
    public void deleteVariable(UUID variableId, UUID userId) {
        EnvironmentVariable variable =
                variableRepository
                        .findById(variableId)
                        .orElseThrow(() -> NotFoundException.of("Environment variable", variableId));
        Environment environment =
                requireEnvironmentAccess(variable.getEnvironmentId(), userId, Permission.MANAGE_VARIABLES);
        String key = variable.getKey();
        variableService.delete(variableId);
        recordVariableActivity(environment, userId, ActivityAction.VARIABLE_DELETED, Map.of("key", key));
    }

    @Transactional
    public BulkVariablesResponse bulkUpdateVariables(
            UUID environmentId, UUID userId, String content, boolean replaceExisting) {
        Environment environment =
                requireEnvironmentAccess(environmentId, userId, Permission.MANAGE_VARIABLES);
        BulkVariablesResponse result =
                variableService.applyBulk(environmentId, content, replaceExisting);
        recordVariableActivity(
                environment,
                userId,
                ActivityAction.VARIABLES_BULK_UPDATED,
                Map.of(
                        "created", result.created(),
                        "updated", result.updated(),
                        "deleted", result.deleted()));
        return result;
    }

    private void recordVariableActivity(
            Environment environment, UUID userId, ActivityAction action, Map<String, Object> metadata) {
        Project project = projectService.requireProject(environment.getProjectId());
        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                userId,
                projectService.actorName(userId),
                action,
                "ENVIRONMENT_VARIABLE",
                environment.getId().toString(),
                metadata);
    }
}
