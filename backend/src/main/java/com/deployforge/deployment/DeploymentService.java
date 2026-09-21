package com.deployforge.deployment;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.common.api.PageResponse;
import com.deployforge.common.error.ErrorCode;
import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.InvalidStateException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.util.Validators;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.dto.DeploymentDtos.CreateDeploymentRequest;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentResponse;
import com.deployforge.deployment.dto.DeploymentDtos.DeploymentSummaryResponse;
import com.deployforge.deployment.dto.DeploymentDtos.QueueStatusResponse;
import com.deployforge.deployment.dto.DeploymentDtos.RollbackCandidate;
import com.deployforge.deployment.dto.DeploymentDtos.StepResponse;
import com.deployforge.deployment.queue.DeploymentQueue;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.environment.EnvironmentType;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.project.ProjectStatus;
import com.deployforge.security.RateLimitService;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.workspace.Permission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The deployment API's service layer: create, inspect, cancel, redeploy and roll back.
 *
 * <p>Creating a deployment is deliberately cheap. It validates, snapshots the project's build
 * configuration onto the new row, persists it as {@code QUEUED} and hands the id to the queue. All the
 * slow work happens in {@code DeploymentWorker}. That is what lets {@code POST /deployments} answer in
 * milliseconds and lets the browser follow progress over WebSocket instead of holding a request open.
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final int NUMBER_ALLOCATION_ATTEMPTS = 5;
    private static final int MAX_ROLLBACK_CANDIDATES = 20;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentStepRepository stepRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectAccessGuard accessGuard;
    private final DeploymentQueue queue;
    private final DeploymentMapper mapper;
    private final DeploymentStateService stateService;
    private final DeploymentPromotionService promotionService;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final RateLimitService rateLimitService;
    private final DeployForgeProperties properties;
    private final com.deployforge.auth.DeployAllowlistGuard deployAllowlistGuard;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            DeploymentStepRepository stepRepository,
            EnvironmentRepository environmentRepository,
            ProjectAccessGuard accessGuard,
            DeploymentQueue queue,
            DeploymentMapper mapper,
            DeploymentStateService stateService,
            DeploymentPromotionService promotionService,
            UserRepository userRepository,
            ActivityService activityService,
            RateLimitService rateLimitService,
            DeployForgeProperties properties,
            com.deployforge.auth.DeployAllowlistGuard deployAllowlistGuard) {
        this.deploymentRepository = deploymentRepository;
        this.stepRepository = stepRepository;
        this.environmentRepository = environmentRepository;
        this.accessGuard = accessGuard;
        this.queue = queue;
        this.mapper = mapper;
        this.stateService = stateService;
        this.promotionService = promotionService;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.deployAllowlistGuard = deployAllowlistGuard;
    }

    // ------------------------------------------------------------------ create

    public DeploymentResponse create(UUID projectId, UUID userId, CreateDeploymentRequest request) {
        Project project = accessGuard.require(projectId, userId, Permission.DEPLOY);
        deployAllowlistGuard.requireCanDeploy(userId);
        rateLimitService.checkPerMinute(
                "deployment", userId.toString(), properties.rateLimit().deploymentPerMinute());

        requireDeployable(project);
        Environment environment = resolveEnvironment(project, request.environmentId());
        String branch =
                StringUtils.hasText(request.branch())
                        ? Validators.requireBranch(request.branch())
                        : environment.getBranch();

        Deployment deployment =
                persistQueuedDeployment(
                        project,
                        environment,
                        branch,
                        DeploymentTriggerType.MANUAL,
                        userId,
                        null,
                        StringUtils.hasText(request.commitSha())
                                ? Validators.requireCommitSha(request.commitSha())
                                : null);

        enqueue(deployment, project, userId, ActivityAction.DEPLOYMENT_CREATED);
        return get(deployment.getId(), userId);
    }

    /**
     * Creates a deployment triggered by a webhook.
     *
     * <p>No interactive user, so authorization is by the fact that a signed webhook matched a project
     * whose environment opted into auto deploy.
     */
    public Deployment createFromWebhook(
            Project project,
            Environment environment,
            String branch,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            DeploymentTriggerType triggerType) {
        requireDeployable(project);
        Deployment deployment =
                persistQueuedDeployment(
                        project, environment, branch, triggerType, project.getCreatedBy(), null, commitSha);
        if (commitMessage != null || commitAuthor != null) {
            stateService.recordCommit(deployment.getId(), commitSha, commitMessage, commitAuthor);
        }
        enqueue(deployment, project, null, ActivityAction.AUTO_DEPLOY_TRIGGERED);
        return deployment;
    }

    public DeploymentResponse redeploy(UUID deploymentId, UUID userId) {
        Deployment source = require(deploymentId);
        Project project = accessGuard.require(source.getProjectId(), userId, Permission.DEPLOY);
        deployAllowlistGuard.requireCanDeploy(userId);
        rateLimitService.checkPerMinute(
                "deployment", userId.toString(), properties.rateLimit().deploymentPerMinute());
        requireDeployable(project);

        Environment environment = requireEnvironment(source.getEnvironmentId());
        Deployment deployment =
                persistQueuedDeployment(
                        project,
                        environment,
                        source.getBranch(),
                        DeploymentTriggerType.REDEPLOY,
                        userId,
                        source.getId(),
                        source.getCommitSha());

        enqueue(deployment, project, userId, ActivityAction.DEPLOYMENT_CREATED);
        return get(deployment.getId(), userId);
    }

    /**
     * Rolls back to a previous successful deployment.
     *
     * <p>History is never rewritten: this creates a <em>new</em> deployment with
     * {@code triggerType=ROLLBACK} that reuses the target's already built image, so the rollback is fast
     * and byte-identical to what was previously running.
     */
    public DeploymentResponse rollback(UUID targetDeploymentId, UUID userId) {
        Deployment target = require(targetDeploymentId);
        Project project = accessGuard.require(target.getProjectId(), userId, Permission.ROLLBACK);
        deployAllowlistGuard.requireCanDeploy(userId);
        rateLimitService.checkPerMinute(
                "deployment", userId.toString(), properties.rateLimit().deploymentPerMinute());
        requireDeployable(project);

        if (target.getStatus() != DeploymentStatus.READY && target.getStatus() != DeploymentStatus.STOPPED) {
            throw new InvalidStateException(
                    "Only deployments that reached READY can be rolled back to. Deployment #"
                            + target.getDeploymentNumber()
                            + " is "
                            + target.getStatus()
                            + ".");
        }
        if (!target.hasReusableImage()) {
            throw new InvalidStateException(
                    ErrorCode.ROLLBACK_NOT_POSSIBLE,
                    "Deployment #"
                            + target.getDeploymentNumber()
                            + " has no image on this host any more. Redeploy the commit instead.");
        }
        if (target.isPromoted()) {
            throw new InvalidStateException(
                    "Deployment #" + target.getDeploymentNumber() + " is already the live deployment.");
        }

        Environment environment = requireEnvironment(target.getEnvironmentId());
        Deployment deployment =
                persistQueuedDeployment(
                        project,
                        environment,
                        target.getBranch(),
                        DeploymentTriggerType.ROLLBACK,
                        userId,
                        target.getId(),
                        target.getCommitSha());

        // Reuse the target's snapshot verbatim - a rollback must not silently pick up new settings.
        copySnapshot(target, deployment.getId());
        stateService.recordCommit(
                deployment.getId(), target.getCommitSha(), target.getCommitMessage(), target.getCommitAuthor());

        enqueue(deployment, project, userId, ActivityAction.DEPLOYMENT_ROLLED_BACK);
        log.info(
                "rollback_created deployment={} target={} project={}",
                deployment.getId(),
                target.getId(),
                project.getId());
        return get(deployment.getId(), userId);
    }

    public void cancel(UUID deploymentId, UUID userId) {
        Deployment deployment = require(deploymentId);
        accessGuard.require(deployment.getProjectId(), userId, Permission.CANCEL_DEPLOYMENT);

        if (!deployment.getStatus().isCancellable()) {
            throw new InvalidStateException(
                    "Deployment #"
                            + deployment.getDeploymentNumber()
                            + " is "
                            + deployment.getStatus()
                            + " and can no longer be cancelled.");
        }
        // Both flags: Redis is what the running pipeline polls, the column is what the API reports.
        queue.requestCancellation(deploymentId);
        stateService.requestCancellation(deploymentId);
        log.info("deployment_cancellation_requested deployment={} by={}", deploymentId, userId);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageResponse<DeploymentSummaryResponse> list(
            UUID projectId, UUID userId, UUID environmentId, int page, int size) {
        accessGuard.requireVisible(projectId, userId);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Deployment> deployments =
                environmentId == null
                        ? deploymentRepository.findByProjectIdOrderByDeploymentNumberDesc(projectId, pageRequest)
                        : deploymentRepository.findByProjectIdAndEnvironmentIdOrderByDeploymentNumberDesc(
                                projectId, environmentId, pageRequest);

        Map<UUID, String> environmentNames = environmentNames(projectId);
        return PageResponse.from(
                deployments,
                deployment ->
                        mapper.toSummary(
                                deployment, environmentNames.get(deployment.getEnvironmentId())));
    }

    @Transactional(readOnly = true)
    public DeploymentResponse get(UUID deploymentId, UUID userId) {
        Deployment deployment = require(deploymentId);
        Project project = accessGuard.requireVisible(deployment.getProjectId(), userId);
        String environmentName =
                environmentRepository
                        .findById(deployment.getEnvironmentId())
                        .map(Environment::getName)
                        .orElse(null);
        User createdBy =
                deployment.getCreatedBy() == null
                        ? null
                        : userRepository.findById(deployment.getCreatedBy()).orElse(null);
        List<DeploymentStep> steps =
                stepRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId);

        String commitUrl =
                deployment.getCommitSha() == null
                        ? null
                        : project.getRepositoryUrl() + "/commit/" + deployment.getCommitSha();

        return mapper.toResponse(deployment, project, environmentName, createdBy, steps, commitUrl);
    }

    @Transactional(readOnly = true)
    public List<StepResponse> steps(UUID deploymentId, UUID userId) {
        Deployment deployment = require(deploymentId);
        accessGuard.requireVisible(deployment.getProjectId(), userId);
        return stepRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId).stream()
                .map(mapper::toStepResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RollbackCandidate> rollbackCandidates(UUID deploymentId, UUID userId) {
        Deployment deployment = require(deploymentId);
        accessGuard.requireVisible(deployment.getProjectId(), userId);
        return deploymentRepository
                .findRollbackCandidates(
                        deployment.getEnvironmentId(),
                        deployment.getId(),
                        PageRequest.of(0, MAX_ROLLBACK_CANDIDATES))
                .stream()
                .map(
                        candidate ->
                                new RollbackCandidate(
                                        candidate.getId(),
                                        candidate.getDeploymentNumber(),
                                        candidate.getCommitSha(),
                                        candidate.shortCommitSha(),
                                        candidate.getCommitMessage(),
                                        candidate.getCreatedAt(),
                                        candidate.hasReusableImage()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> currentDeployment(UUID environmentId) {
        return deploymentRepository.findFirstByEnvironmentIdAndPromotedTrueOrderByDeploymentNumberDesc(
                environmentId);
    }

    @Transactional(readOnly = true)
    public Deployment require(UUID deploymentId) {
        return deploymentRepository
                .findById(deploymentId)
                .orElseThrow(() -> NotFoundException.of("Deployment", deploymentId));
    }

    public QueueStatusResponse queueStatus() {
        return new QueueStatusResponse(queue.depth(), properties.deployment().workerConcurrency());
    }

    // ------------------------------------------------------------------ internals

    /**
     * Persists a {@code QUEUED} deployment, snapshotting the project's current build configuration.
     *
     * <p>The deployment number is allocated with a retry loop: the unique constraint on
     * {@code (project_id, deployment_number)} is the real guard, so two concurrent deploys can never end
     * up as the same "#42".
     */
    @Transactional
    public Deployment persistQueuedDeployment(
            Project project,
            Environment environment,
            String branch,
            DeploymentTriggerType triggerType,
            UUID createdBy,
            UUID sourceDeploymentId,
            String commitSha) {

        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= NUMBER_ALLOCATION_ATTEMPTS; attempt++) {
            int number = deploymentRepository.findMaxDeploymentNumber(project.getId()) + 1;
            Deployment deployment =
                    new Deployment(
                            project.getId(),
                            environment.getId(),
                            number,
                            branch,
                            triggerType,
                            createdBy);
            deployment.setSourceDeploymentId(sourceDeploymentId);
            deployment.setCommitSha(commitSha);
            applySnapshot(project, deployment);
            try {
                return deploymentRepository.saveAndFlush(deployment);
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
                log.debug(
                        "deployment_number_collision project={} number={} attempt={}",
                        project.getId(),
                        number,
                        attempt);
            }
        }
        log.error(
                "deployment_number_allocation_exhausted project={} reason={}",
                project.getId(),
                lastFailure == null ? "unknown" : lastFailure.getMostSpecificCause().getMessage());
        throw new BadRequestException(
                "Could not allocate a deployment number after "
                        + NUMBER_ALLOCATION_ATTEMPTS
                        + " attempts because other deployments were created at the same time. Try again in a moment.");
    }

    private void applySnapshot(Project project, Deployment deployment) {
        deployment.setFramework(project.getFramework());
        deployment.setRuntime(project.getRuntime());
        deployment.setRootDirectory(project.getRootDirectory());
        deployment.setInstallCommand(project.getInstallCommand());
        deployment.setBuildCommand(project.getBuildCommand());
        deployment.setStartCommand(project.getStartCommand());
        deployment.setDockerfilePath(project.getDockerfilePath());
        deployment.setContainerPort(project.getContainerPort());
    }

    /**
     * Copies a build snapshot onto an existing deployment.
     *
     * <p>Loads, mutates and saves explicitly rather than relying on dirty checking, because this is
     * called from a non transactional path and an unflushed change would be silently lost.
     */
    public void copySnapshot(Deployment source, UUID targetId) {
        Deployment managed = require(targetId);
        managed.setFramework(source.getFramework());
        managed.setRuntime(source.getRuntime());
        managed.setRootDirectory(source.getRootDirectory());
        managed.setInstallCommand(source.getInstallCommand());
        managed.setBuildCommand(source.getBuildCommand());
        managed.setStartCommand(source.getStartCommand());
        managed.setDockerfilePath(source.getDockerfilePath());
        managed.setContainerPort(source.getContainerPort());
        deploymentRepository.save(managed);
    }

    private void enqueue(
            Deployment deployment, Project project, UUID actorId, ActivityAction action) {
        queue.enqueue(deployment.getId());
        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                actorId,
                actorId == null
                        ? "GitHub"
                        : userRepository.findById(actorId).map(User::displayName).orElse("unknown"),
                action,
                "DEPLOYMENT",
                deployment.getId().toString(),
                Map.of(
                        "deploymentNumber", deployment.getDeploymentNumber(),
                        "branch", deployment.getBranch(),
                        "trigger", deployment.getTriggerType().name()));
        log.info(
                "deployment_queued deployment={} project={} number={} trigger={}",
                deployment.getId(),
                project.getId(),
                deployment.getDeploymentNumber(),
                deployment.getTriggerType());
    }

    private void requireDeployable(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new InvalidStateException("Archived projects cannot be deployed. Reactivate it first.");
        }
        if (project.getStatus() == ProjectStatus.PAUSED) {
            throw new InvalidStateException(
                    "This project is paused. Resume it in project settings before deploying.");
        }
        if (project.getFramework() == null) {
            throw new BadRequestException(
                    "This project has no framework configured yet. Open project settings and choose one.");
        }
        if (project.getContainerPort() == null) {
            throw new BadRequestException(
                    "This project has no port configured. Set the port your application listens on.");
        }
    }

    private Environment resolveEnvironment(Project project, UUID environmentId) {
        if (environmentId != null) {
            Environment environment = requireEnvironment(environmentId);
            if (!environment.getProjectId().equals(project.getId())) {
                throw new BadRequestException("That environment belongs to a different project");
            }
            return environment;
        }
        return environmentRepository
                .findFirstByProjectIdAndType(project.getId(), EnvironmentType.PRODUCTION)
                .or(
                        () ->
                                environmentRepository
                                        .findByProjectIdOrderByTypeAscNameAsc(project.getId())
                                        .stream()
                                        .findFirst())
                .orElseThrow(
                        () ->
                                new BadRequestException(
                                        "This project has no environment to deploy to. Create one first."));
    }

    private Environment requireEnvironment(UUID environmentId) {
        return environmentRepository
                .findById(environmentId)
                .orElseThrow(() -> NotFoundException.of("Environment", environmentId));
    }

    private Map<UUID, String> environmentNames(UUID projectId) {
        Map<UUID, String> names = new HashMap<>();
        environmentRepository
                .findByProjectIdOrderByTypeAscNameAsc(projectId)
                .forEach(environment -> names.put(environment.getId(), environment.getName()));
        return names;
    }

    /** Exposed for the monitoring and AI modules, which need the promoted deployment of a project. */
    @Transactional(readOnly = true)
    public Optional<Deployment> latestDeployment(UUID projectId) {
        return deploymentRepository.findFirstByProjectIdOrderByDeploymentNumberDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<Deployment> liveDeployments(UUID projectId) {
        return promotionService.liveDeployments(projectId);
    }
}
