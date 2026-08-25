package com.deployforge.monitoring;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.monitoring.dto.MonitoringDtos.DeploymentMetricsResponse;
import com.deployforge.monitoring.dto.MonitoringDtos.MetricSample;
import com.deployforge.monitoring.dto.MonitoringDtos.ProjectHealthResponse;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.RuntimeModels.RuntimeStats;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Container health and resource usage.
 *
 * <p>Reads come from the Docker engine when the container is alive (so the numbers are current) and fall
 * back to the last stored sample when it is not. Charts are drawn from the stored samples.
 *
 * <p>Request counts are deliberately absent: DeployForge does not sit in the request path on a single node
 * install, so inventing a request metric would be a fake dashboard.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final int MAX_HISTORY_SAMPLES = 240;

    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentMetricRepository metricRepository;
    private final DeploymentRuntime runtime;
    private final DeployForgeProperties properties;

    public MonitoringService(
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            DeploymentMetricRepository metricRepository,
            DeploymentRuntime runtime,
            DeployForgeProperties properties) {
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.metricRepository = metricRepository;
        this.runtime = runtime;
        this.properties = properties;
    }

    /** Health of the project's currently promoted deployment. */
    @Transactional(readOnly = true)
    public ProjectHealthResponse projectHealth(UUID projectId) {
        Optional<Deployment> promoted = promotedDeployment(projectId);
        if (promoted.isEmpty()) {
            Optional<Deployment> latest =
                    deploymentRepository.findFirstByProjectIdOrderByDeploymentNumberDesc(projectId);
            if (latest.isEmpty()) {
                return ProjectHealthResponse.noDeployment(projectId, "This project has not been deployed yet.");
            }
            Deployment deployment = latest.get();
            return new ProjectHealthResponse(
                    projectId,
                    deployment.getStatus() == DeploymentStatus.FAILED ? "FAILED" : "NOT_LIVE",
                    deployment.getEnvironmentId(),
                    environmentName(deployment.getEnvironmentId()),
                    deployment.getId(),
                    deployment.getDeploymentNumber(),
                    deployment.getStatus().name(),
                    deployment.getDeploymentUrl(),
                    deployment.shortCommitSha(),
                    deployment.getFinishedAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    deployment.getStatus() == DeploymentStatus.FAILED
                            ? "The most recent deployment failed, so nothing is currently serving traffic."
                            : "No deployment is currently live for this project.");
        }

        Deployment deployment = promoted.get();
        Optional<RuntimeStats> stats = runtime.getStats(deployment.getContainerId());
        Optional<DeploymentMetric> lastSample = latestSample(deployment.getId());

        String containerStatus =
                stats.map(RuntimeStats::status)
                        .orElseGet(() -> lastSample.map(DeploymentMetric::getContainerStatus).orElse("unknown"));
        boolean healthy = "running".equalsIgnoreCase(containerStatus);

        Instant startedAt = stats.map(RuntimeStats::startedAt).orElse(deployment.getFinishedAt());
        Long uptimeSeconds =
                startedAt == null ? null : Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds());

        return new ProjectHealthResponse(
                projectId,
                healthy ? "HEALTHY" : "UNHEALTHY",
                deployment.getEnvironmentId(),
                environmentName(deployment.getEnvironmentId()),
                deployment.getId(),
                deployment.getDeploymentNumber(),
                deployment.getStatus().name(),
                deployment.getDeploymentUrl(),
                deployment.shortCommitSha(),
                deployment.getFinishedAt(),
                uptimeSeconds,
                containerStatus,
                stats.map(RuntimeStats::restartCount)
                        .orElseGet(() -> lastSample.map(DeploymentMetric::getRestartCount).orElse(null)),
                stats.map(RuntimeStats::cpuPercent)
                        .orElseGet(() -> lastSample.map(DeploymentMetric::getCpuPercent).orElse(null)),
                stats.map(RuntimeStats::memoryBytes)
                        .orElseGet(() -> lastSample.map(DeploymentMetric::getMemoryBytes).orElse(null)),
                stats.map(RuntimeStats::memoryLimitBytes)
                        .orElseGet(() -> lastSample.map(DeploymentMetric::getMemoryLimitBytes).orElse(null)),
                lastSample.map(DeploymentMetric::getSampledAt).orElse(null),
                healthy
                        ? "Container is running."
                        : "Container state is '" + containerStatus + "'.");
    }

    /** Current stats plus recent history for one deployment. */
    @Transactional(readOnly = true)
    public DeploymentMetricsResponse deploymentMetrics(UUID deploymentId, Duration window) {
        Deployment deployment =
                deploymentRepository
                        .findById(deploymentId)
                        .orElseThrow(
                                () ->
                                        new com.deployforge.common.error.Exceptions.NotFoundException(
                                                "Deployment " + deploymentId + " was not found"));

        Optional<RuntimeStats> stats = runtime.getStats(deployment.getContainerId());
        List<MetricSample> history =
                metricRepository
                        .findByDeploymentIdAndSampledAtAfterOrderBySampledAtAsc(
                                deploymentId, Instant.now().minus(window))
                        .stream()
                        .sorted(Comparator.comparing(DeploymentMetric::getSampledAt))
                        .limit(MAX_HISTORY_SAMPLES)
                        .map(
                                sample ->
                                        new MetricSample(
                                                sample.getSampledAt(),
                                                sample.getCpuPercent(),
                                                sample.getMemoryBytes(),
                                                sample.getMemoryLimitBytes(),
                                                sample.getRestartCount(),
                                                sample.getContainerStatus()))
                        .toList();

        Instant startedAt = stats.map(RuntimeStats::startedAt).orElse(null);
        return new DeploymentMetricsResponse(
                deploymentId,
                stats.map(RuntimeStats::status).orElse("unknown"),
                stats.map(RuntimeStats::cpuPercent).orElse(null),
                stats.map(RuntimeStats::memoryBytes).orElse(null),
                stats.map(RuntimeStats::memoryLimitBytes)
                        .orElse(properties.deployment().memoryLimitBytes()),
                stats.map(RuntimeStats::restartCount).orElse(null),
                startedAt,
                startedAt == null ? null : Duration.between(startedAt, Instant.now()).toSeconds(),
                history,
                stats.isPresent());
    }

    /** Persists one sample. Called by the sampling job. */
    @Transactional
    public Optional<DeploymentMetric> sample(Deployment deployment) {
        Optional<RuntimeStats> stats = runtime.getStats(deployment.getContainerId());
        if (stats.isEmpty()) {
            return Optional.empty();
        }
        RuntimeStats current = stats.get();
        DeploymentMetric metric =
                new DeploymentMetric(
                        deployment.getId(),
                        deployment.getProjectId(),
                        Instant.now(),
                        current.cpuPercent(),
                        current.memoryBytes(),
                        current.memoryLimitBytes(),
                        current.restartCount(),
                        current.status());
        return Optional.of(metricRepository.save(metric));
    }

    @Transactional(readOnly = true)
    public Optional<DeploymentMetric> latestSample(UUID deploymentId) {
        return metricRepository
                .findByDeploymentIdOrderBySampledAtDesc(deploymentId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /** Deployments the sampler should poll: promoted, READY and with a container. */
    @Transactional(readOnly = true)
    public List<Deployment> liveDeployments() {
        return deploymentRepository.findLiveContainers();
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return metricRepository.deleteOlderThan(cutoff);
    }

    private Optional<Deployment> promotedDeployment(UUID projectId) {
        return environmentRepository.findByProjectIdOrderByTypeAscNameAsc(projectId).stream()
                .map(Environment::getId)
                .map(
                        environmentId ->
                                deploymentRepository
                                        .findFirstByEnvironmentIdAndPromotedTrueOrderByDeploymentNumberDesc(
                                                environmentId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private String environmentName(UUID environmentId) {
        return environmentRepository.findById(environmentId).map(Environment::getName).orElse(null);
    }
}
