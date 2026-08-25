package com.deployforge.monitoring;

import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.event.DeploymentEvent;
import com.deployforge.deployment.event.DeploymentEventPublisher;
import com.deployforge.notification.NotificationService;
import com.deployforge.notification.NotificationType;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Samples container stats for every live deployment on a fixed interval.
 *
 * <p>Interval sampling rather than on-demand polling from each browser: ten open dashboards should not mean
 * ten Docker stats calls per second. Samples are stored once and every client reads the same data.
 *
 * <p>The job also detects a container that has stopped or restarted since the last sample and raises a
 * notification, which is how a crash-looping application becomes visible without anyone watching a chart.
 */
@Component
public class MetricsSamplingJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsSamplingJob.class);

    private final MonitoringService monitoringService;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    private final ObjectProvider<DeploymentEventPublisher> eventPublisher;

    /** Restart counts from the previous run, used to detect new restarts. */
    private final Map<UUID, Integer> lastRestartCounts = new HashMap<>();

    public MetricsSamplingJob(
            MonitoringService monitoringService,
            ProjectRepository projectRepository,
            NotificationService notificationService,
            ObjectProvider<DeploymentEventPublisher> eventPublisher) {
        this.monitoringService = monitoringService;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(
            initialDelayString = "${deployforge.deployment.metrics-interval}",
            fixedDelayString = "${deployforge.deployment.metrics-interval}")
    public void sampleLiveDeployments() {
        for (Deployment deployment : monitoringService.liveDeployments()) {
            try {
                Optional<DeploymentMetric> sample = monitoringService.sample(deployment);
                if (sample.isEmpty()) {
                    // Container is gone but the deployment still claims to be live.
                    handleMissingContainer(deployment);
                    continue;
                }
                DeploymentMetric metric = sample.get();
                publish(deployment.getId(), metric);
                detectRestart(deployment, metric);
            } catch (RuntimeException e) {
                // One unhealthy container must not stop the whole sampling pass.
                log.debug(
                        "metrics_sample_failed deployment={} reason={}", deployment.getId(), e.getMessage());
            }
        }
    }

    private void handleMissingContainer(Deployment deployment) {
        Integer previous = lastRestartCounts.remove(deployment.getId());
        if (previous == null) {
            return;
        }
        log.warn(
                "live_container_missing deployment={} container={}",
                deployment.getId(),
                deployment.getContainerId());
        projectRepository
                .findById(deployment.getProjectId())
                .ifPresent(
                        project ->
                                notificationService.notifyWorkspace(
                                        project.getWorkspaceId(),
                                        NotificationType.HEALTH_CHECK_FAILED,
                                        project.getName()
                                                + " deployment #"
                                                + deployment.getDeploymentNumber()
                                                + " is no longer running",
                                        "The container disappeared from the Docker engine.",
                                        project.getId(),
                                        deployment.getId()));
    }

    private void detectRestart(Deployment deployment, DeploymentMetric metric) {
        int restarts = metric.getRestartCount() == null ? 0 : metric.getRestartCount();
        Integer previous = lastRestartCounts.put(deployment.getId(), restarts);
        boolean notRunning =
                metric.getContainerStatus() != null
                        && !"running".equalsIgnoreCase(metric.getContainerStatus());

        if (previous != null && restarts > previous) {
            Optional<Project> project = projectRepository.findById(deployment.getProjectId());
            project.ifPresent(
                    value ->
                            notificationService.notifyWorkspace(
                                    value.getWorkspaceId(),
                                    NotificationType.HEALTH_CHECK_FAILED,
                                    value.getName()
                                            + " deployment #"
                                            + deployment.getDeploymentNumber()
                                            + " restarted",
                                    "Restart count went from " + previous + " to " + restarts + ".",
                                    value.getId(),
                                    deployment.getId()));
            log.warn(
                    "container_restart_detected deployment={} restarts={} previous={}",
                    deployment.getId(),
                    restarts,
                    previous);
        } else if (notRunning) {
            log.warn(
                    "container_not_running deployment={} status={}",
                    deployment.getId(),
                    metric.getContainerStatus());
        }
    }

    private void publish(UUID deploymentId, DeploymentMetric metric) {
        DeploymentEventPublisher publisher = eventPublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        publisher.publish(
                new DeploymentEvent.Metrics(
                        deploymentId,
                        metric.getCpuPercent(),
                        metric.getMemoryBytes(),
                        metric.getMemoryLimitBytes(),
                        metric.getRestartCount(),
                        metric.getContainerStatus()));
    }
}
