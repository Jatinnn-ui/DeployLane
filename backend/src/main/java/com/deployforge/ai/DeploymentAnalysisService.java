package com.deployforge.ai;

import com.deployforge.activity.ActivityAction;
import com.deployforge.activity.ActivityService;
import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.SuggestedFix;
import com.deployforge.ai.DeploymentAnalysis.AnalysisStatus;
import com.deployforge.ai.dto.AiDtos.AnalysisResponse;
import com.deployforge.common.error.Exceptions.InvalidStateException;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentService;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.deployment.event.DeploymentEvent;
import com.deployforge.deployment.event.DeploymentEventPublisher;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.notification.NotificationService;
import com.deployforge.notification.NotificationType;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectAccessGuard;
import com.deployforge.security.RateLimitService;
import com.deployforge.workspace.Permission;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces and stores AI analysis for failed deployments.
 *
 * <p>Order of operations matters and is deliberate:
 *
 * <ol>
 *   <li>only {@code FAILED} deployments are analysed - there is nothing to explain otherwise
 *   <li>evidence is gathered from persisted logs, which have already been redacted on write, plus variable
 *       <em>names</em> and the build configuration
 *   <li>the configured provider is tried, and on any failure the local heuristic analyzer runs instead, so
 *       the card is never simply empty
 *   <li>the validated result is cached on the deployment; repeat requests do not re-bill the provider
 * </ol>
 */
@Service
public class DeploymentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentAnalysisService.class);
    private static final int MAX_ERROR_LINES = 40;
    private static final int MAX_DOCKERFILE_CHARS = 4000;

    private final DeploymentAnalysisRepository analysisRepository;
    private final DeploymentService deploymentService;
    private final DeploymentLogService logService;
    private final EnvironmentVariableService variableService;
    private final ProjectAccessGuard accessGuard;
    private final AiProviderRegistry providerRegistry;
    private final JsonCodec json;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final DeployForgeProperties properties;
    private final ObjectProvider<DeploymentEventPublisher> eventPublisher;

    public DeploymentAnalysisService(
            DeploymentAnalysisRepository analysisRepository,
            DeploymentService deploymentService,
            DeploymentLogService logService,
            EnvironmentVariableService variableService,
            ProjectAccessGuard accessGuard,
            AiProviderRegistry providerRegistry,
            JsonCodec json,
            ActivityService activityService,
            NotificationService notificationService,
            RateLimitService rateLimitService,
            DeployForgeProperties properties,
            ObjectProvider<DeploymentEventPublisher> eventPublisher) {
        this.analysisRepository = analysisRepository;
        this.deploymentService = deploymentService;
        this.logService = logService;
        this.variableService = variableService;
        this.accessGuard = accessGuard;
        this.providerRegistry = providerRegistry;
        this.json = json;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ API surface

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID deploymentId, UUID userId) {
        Deployment deployment = deploymentService.require(deploymentId);
        accessGuard.require(deployment.getProjectId(), userId, Permission.VIEW_ANALYSIS);
        return analysisRepository
                .findByDeploymentId(deploymentId)
                .map(this::toResponse)
                .orElseGet(
                        () ->
                                deployment.getStatus() == DeploymentStatus.FAILED
                                        ? AnalysisResponse.unavailable(
                                                deploymentId,
                                                "No analysis has been generated for this deployment yet.")
                                        : AnalysisResponse.unavailable(
                                                deploymentId,
                                                "Only failed deployments are analysed. This deployment is "
                                                        + deployment.getStatus()
                                                        + "."));
    }

    /** User initiated analysis, or re-analysis with {@code force}. */
    public AnalysisResponse analyzeOnDemand(UUID deploymentId, UUID userId, boolean force) {
        Deployment deployment = deploymentService.require(deploymentId);
        Project project = accessGuard.require(deployment.getProjectId(), userId, Permission.USE_AI);
        rateLimitService.checkPerMinute(
                "ai-analysis", userId.toString(), properties.rateLimit().aiAnalysisPerMinute());

        if (deployment.getStatus() != DeploymentStatus.FAILED) {
            throw new InvalidStateException(
                    "Only failed deployments can be analysed. Deployment #"
                            + deployment.getDeploymentNumber()
                            + " is "
                            + deployment.getStatus()
                            + ".");
        }

        Optional<DeploymentAnalysis> existing = analysisRepository.findByDeploymentId(deploymentId);
        if (existing.isPresent() && existing.get().getStatus() == AnalysisStatus.COMPLETED && !force) {
            return toResponse(existing.get());
        }
        return toResponse(runAnalysis(deployment, project));
    }

    /**
     * Called by the pipeline when a deployment fails.
     *
     * <p>Runs on a separate thread and never rethrows: a model being slow or down must not affect the
     * deployment's outcome, which has already been recorded by the time this runs.
     */
    public void analyzeAsync(UUID deploymentId) {
        Thread.ofVirtual()
                .name("df-analysis-" + deploymentId)
                .start(
                        () -> {
                            try {
                                Deployment deployment = deploymentService.require(deploymentId);
                                if (deployment.getStatus() != DeploymentStatus.FAILED) {
                                    return;
                                }
                                Project project = deploymentProject(deployment);
                                runAnalysis(deployment, project);
                            } catch (RuntimeException e) {
                                log.warn(
                                        "analysis_async_failed deployment={} reason={}", deploymentId, e.getMessage());
                            }
                        });
    }

    // ------------------------------------------------------------------ core

    private DeploymentAnalysis runAnalysis(Deployment deployment, Project project) {
        DeploymentAnalysis analysis = beginAnalysis(deployment.getId());
        logService.append(
                deployment.getId(),
                LogLevel.INFO,
                LogSource.AI,
                "Analysing this failure with the "
                        + providerRegistry.primary().name()
                        + " analyzer...");

        AiAnalysisContext context = buildContext(deployment, project);

        AiAnalysisResult result = null;
        String failureReason = null;
        AiProvider primary = providerRegistry.primary();
        try {
            result = primary.analyzeDeploymentFailure(context);
        } catch (AiProviderException e) {
            failureReason = e.getMessage();
            log.warn("ai_primary_failed provider={} reason={}", primary.name(), e.getMessage());
        } catch (RuntimeException e) {
            failureReason = "The AI provider failed unexpectedly";
            log.warn("ai_primary_error provider={}", primary.name(), e);
        }

        if (result == null && !primary.name().equals(providerRegistry.fallback().name())) {
            try {
                result = providerRegistry.fallback().analyzeDeploymentFailure(context);
                logService.append(
                        deployment.getId(),
                        LogLevel.WARN,
                        LogSource.AI,
                        "The configured AI provider was unavailable ("
                                + failureReason
                                + "), so the built-in rule based analyzer was used instead.");
            } catch (RuntimeException e) {
                log.warn("ai_fallback_failed reason={}", e.getMessage());
            }
        }

        if (result == null) {
            return markUnavailable(
                    analysis.getDeploymentId(),
                    failureReason == null ? "No analyzer could produce a result" : failureReason);
        }

        DeploymentAnalysis saved = store(deployment.getId(), result);
        logService.append(
                deployment.getId(),
                LogLevel.INFO,
                LogSource.AI,
                "Root cause: " + result.rootCause() + " (confidence " + Math.round(result.confidence() * 100) + "%)");

        publish(
                new DeploymentEvent.AnalysisReady(
                        deployment.getId(),
                        result.severity() == null ? null : result.severity().name(),
                        result.confidence()));

        activityService.record(
                project.getWorkspaceId(),
                project.getId(),
                null,
                result.provider(),
                ActivityAction.AI_ANALYSIS_COMPLETED,
                "DEPLOYMENT",
                deployment.getId().toString(),
                Map.of(
                        "severity", String.valueOf(result.severity()),
                        "confidence", result.confidence(),
                        "provider", String.valueOf(result.provider())));

        notificationService.notifyWorkspace(
                project.getWorkspaceId(),
                NotificationType.AI_ANALYSIS_READY,
                "Failure analysis ready for "
                        + project.getName()
                        + " #"
                        + deployment.getDeploymentNumber(),
                result.rootCause(),
                project.getId(),
                deployment.getId());

        return saved;
    }

    /**
     * Assembles the evidence.
     *
     * <p>Note the two safeguards: only variable <em>names</em> are included, and log lines come from storage
     * where redaction already happened. There is no code path that can send a decrypted value here.
     */
    @Transactional(readOnly = true)
    public AiAnalysisContext buildContext(Deployment deployment, Project project) {
        int maxLogLines = properties.ai().maxLogLines();
        List<String> recentLogs = logService.tailPlain(deployment.getId(), maxLogLines);
        List<String> errorLogs = logService.errorLines(deployment.getId(), MAX_ERROR_LINES);
        List<String> variableNames = variableService.keyNames(deployment.getEnvironmentId());

        String dockerfile = deployment.getGeneratedDockerfile();
        if (dockerfile != null && dockerfile.length() > MAX_DOCKERFILE_CHARS) {
            dockerfile = dockerfile.substring(0, MAX_DOCKERFILE_CHARS) + "\n...[truncated]";
        }

        return new AiAnalysisContext(
                project.getName(),
                deployment.getFramework() == null ? null : deployment.getFramework().displayName(),
                deployment.getRuntime() == null ? null : deployment.getRuntime().displayName(),
                deployment.getFailureStage() == null ? null : deployment.getFailureStage().name(),
                deployment.getFailureMessage(),
                deployment.getExitCode(),
                deployment.getInstallCommand(),
                deployment.getBuildCommand(),
                deployment.getStartCommand(),
                deployment.getContainerPort(),
                project.getHealthCheckPath(),
                deployment.getBranch(),
                deployment.shortCommitSha(),
                dockerfile,
                dockerfile != null,
                variableNames,
                recentLogs,
                errorLogs);
    }

    // ------------------------------------------------------------------ persistence

    @Transactional
    public DeploymentAnalysis beginAnalysis(UUID deploymentId) {
        DeploymentAnalysis analysis =
                analysisRepository
                        .findByDeploymentId(deploymentId)
                        .orElseGet(() -> new DeploymentAnalysis(deploymentId));
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setErrorMessage(null);
        return analysisRepository.save(analysis);
    }

    @Transactional
    public DeploymentAnalysis store(UUID deploymentId, AiAnalysisResult result) {
        DeploymentAnalysis analysis =
                analysisRepository
                        .findByDeploymentId(deploymentId)
                        .orElseGet(() -> new DeploymentAnalysis(deploymentId));
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setSummary(result.summary());
        analysis.setRootCause(result.rootCause());
        analysis.setEvidence(json.write(result.evidence()));
        analysis.setSuggestedFixes(json.write(result.suggestedFixes()));
        analysis.setSeverity(result.severity());
        analysis.setConfidence(result.confidence());
        analysis.setProvider(result.provider());
        analysis.setModel(result.model());
        analysis.setRawProviderResponse(result.rawResponse());
        analysis.setErrorMessage(null);
        return analysisRepository.save(analysis);
    }

    @Transactional
    public DeploymentAnalysis markUnavailable(UUID deploymentId, String reason) {
        DeploymentAnalysis analysis =
                analysisRepository
                        .findByDeploymentId(deploymentId)
                        .orElseGet(() -> new DeploymentAnalysis(deploymentId));
        analysis.setStatus(AnalysisStatus.UNAVAILABLE);
        analysis.setErrorMessage(reason);
        return analysisRepository.save(analysis);
    }

    // ------------------------------------------------------------------ helpers

    public AnalysisResponse toResponse(DeploymentAnalysis analysis) {
        return new AnalysisResponse(
                analysis.getDeploymentId(),
                analysis.getStatus(),
                analysis.getSummary(),
                analysis.getRootCause(),
                json.readStringList(analysis.getEvidence()),
                json.read(
                        analysis.getSuggestedFixes(), new TypeReference<List<SuggestedFix>>() {}, List.of()),
                analysis.getSeverity(),
                analysis.getConfidence(),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getErrorMessage(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
    }

    /** Analysis summary used by the AI chat context. */
    @Transactional(readOnly = true)
    public Optional<DeploymentAnalysis> findForDeployment(UUID deploymentId) {
        return analysisRepository.findByDeploymentId(deploymentId);
    }

    private Project deploymentProject(Deployment deployment) {
        return accessGuard.requireProjectForInternalUse(deployment.getProjectId());
    }

    private void publish(DeploymentEvent event) {
        DeploymentEventPublisher publisher = eventPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish(event);
        }
    }
}
