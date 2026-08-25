package com.deployforge.cleanup;

import com.deployforge.activity.ActivityService;
import com.deployforge.common.util.SafePaths;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.monitoring.MonitoringService;
import com.deployforge.notification.NotificationService;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.webhook.WebhookDeliveryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled retention and garbage collection.
 *
 * <p>The single most important rule here: DeployForge only ever removes resources it created. The Docker
 * engine it talks to also runs the user's own containers, so there is no
 * {@code docker system prune} anywhere in this codebase. Every candidate is matched on the
 * {@code deployforge.managed=true} label or on a deployment row DeployForge owns.
 *
 * <p>Images belonging to deployments that could still be rolled back are preserved even when they are old:
 * deleting a rollback target to save disk space would be the wrong trade.
 */
@Component
public class CleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);
    private static final Duration CONTAINER_STOP_TIMEOUT = Duration.ofSeconds(5);
    private static final int ROLLBACK_IMAGES_TO_KEEP_PER_PROJECT = 5;
    private static final Duration WEBHOOK_RETENTION = Duration.ofDays(7);
    private static final Duration NOTIFICATION_RETENTION = Duration.ofDays(30);
    private static final Duration ACTIVITY_RETENTION = Duration.ofDays(90);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRuntime runtime;
    private final DeploymentLogService logService;
    private final MonitoringService monitoringService;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final DeployForgeProperties properties;

    public CleanupJob(
            DeploymentRepository deploymentRepository,
            DeploymentRuntime runtime,
            DeploymentLogService logService,
            MonitoringService monitoringService,
            NotificationService notificationService,
            ActivityService activityService,
            WebhookDeliveryRepository webhookDeliveryRepository,
            DeployForgeProperties properties) {
        this.deploymentRepository = deploymentRepository;
        this.runtime = runtime;
        this.logService = logService;
        this.monitoringService = monitoringService;
        this.notificationService = notificationService;
        this.activityService = activityService;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.properties = properties;
    }

    /** Runs hourly at 15 past. Each phase is independent so one failure does not skip the rest. */
    @Scheduled(cron = "0 15 * * * *")
    public void run() {
        log.info("cleanup_started");
        safely("build directories", this::cleanBuildDirectories);
        safely("containers", this::cleanContainers);
        safely("images", this::cleanImages);
        safely("logs", this::cleanLogs);
        safely("metrics", this::cleanMetrics);
        safely("notifications", this::cleanNotifications);
        safely("activity", this::cleanActivity);
        safely("webhook deliveries", this::cleanWebhookDeliveries);
        log.info("cleanup_finished");
    }

    // ------------------------------------------------------------------ phases

    /**
     * Removes checkouts of finished deployments once their retention window has passed.
     *
     * <p>Failed deployments keep their working copy for the window on purpose: it is the evidence a
     * developer may want to inspect. Active deployments are never touched.
     */
    @Transactional(readOnly = true)
    public void cleanBuildDirectories() {
        Path root = Path.of(properties.deployment().rootPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.deployment().buildRetentionHours()));
        Set<UUID> activeDeployments = new HashSet<>();
        deploymentRepository.findAllActive().forEach(deployment -> activeDeployments.add(deployment.getId()));

        int removed = 0;
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.toList()) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                UUID deploymentId = parseUuid(directory.getFileName().toString());
                if (deploymentId != null && activeDeployments.contains(deploymentId)) {
                    continue;
                }
                Instant modified = lastModified(directory);
                if (modified != null && modified.isBefore(cutoff)) {
                    SafePaths.deleteQuietly(directory);
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("cleanup_build_dirs_failed reason={}", e.getMessage());
        }
        if (removed > 0) {
            log.info("cleanup_build_dirs removed={}", removed);
        }
    }

    /**
     * Removes exited containers whose deployment is finished and is not the live one.
     *
     * <p>Containers are matched by DeployForge's own label, which also catches containers whose deployment
     * row has been deleted.
     */
    @Transactional(readOnly = true)
    public void cleanContainers() {
        List<DeploymentRuntime.ManagedContainer> containers = runtime.listManagedContainers();
        int removed = 0;
        for (DeploymentRuntime.ManagedContainer container : containers) {
            UUID deploymentId = parseUuid(container.deploymentId());
            if (deploymentId == null) {
                // Managed by DeployForge but no longer tracked: safe to remove if it is not running.
                if (isStopped(container.status())) {
                    runtime.remove(container.containerId());
                    removed++;
                }
                continue;
            }
            Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
            if (deployment == null) {
                runtime.stop(container.containerId(), CONTAINER_STOP_TIMEOUT);
                runtime.remove(container.containerId());
                removed++;
                continue;
            }
            boolean live = deployment.isPromoted() && deployment.getStatus() == DeploymentStatus.READY;
            if (!live && isStopped(container.status())) {
                runtime.remove(container.containerId());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("cleanup_containers removed={}", removed);
        }
    }

    /**
     * Removes images that no deployment needs any more.
     *
     * <p>Kept: the live deployment's image, and the most recent successful images per project so rollback
     * stays possible. Removed: images of failed or cancelled deployments, and images whose deployment row is
     * gone.
     */
    @Transactional(readOnly = true)
    public void cleanImages() {
        Set<String> keep = new HashSet<>();
        Set<String> known = new HashSet<>();

        for (Deployment deployment : deploymentRepository.findAll()) {
            if (deployment.getImageTag() == null) {
                continue;
            }
            known.add(deployment.getImageTag());
            boolean live = deployment.isPromoted() && deployment.getStatus() == DeploymentStatus.READY;
            if (live || deployment.getStatus().isActive()) {
                keep.add(deployment.getImageTag());
            }
        }

        // Preserve the newest rollback candidates per project.
        deploymentRepository.findAll().stream()
                .filter(deployment -> deployment.getImageTag() != null)
                .filter(
                        deployment ->
                                deployment.getStatus() == DeploymentStatus.READY
                                        || deployment.getStatus() == DeploymentStatus.STOPPED)
                .collect(java.util.stream.Collectors.groupingBy(Deployment::getProjectId))
                .forEach(
                        (projectId, deployments) ->
                                deployments.stream()
                                        .sorted(
                                                java.util.Comparator.comparingInt(Deployment::getDeploymentNumber)
                                                        .reversed())
                                        .limit(ROLLBACK_IMAGES_TO_KEEP_PER_PROJECT)
                                        .forEach(deployment -> keep.add(deployment.getImageTag())));

        int removed = 0;
        for (String tag : runtime.listManagedImages()) {
            if (keep.contains(tag)) {
                continue;
            }
            if (!known.contains(tag) || !keep.contains(tag)) {
                runtime.removeImage(tag);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("cleanup_images removed={}", removed);
        }
    }

    public void cleanLogs() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.deployment().logRetentionDays()));
        int deleted = logService.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("cleanup_logs deleted={} cutoff={}", deleted, cutoff);
        }
    }

    public void cleanMetrics() {
        Instant cutoff =
                Instant.now().minus(Duration.ofHours(properties.deployment().metricsRetentionHours()));
        int deleted = monitoringService.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("cleanup_metrics deleted={}", deleted);
        }
    }

    public void cleanNotifications() {
        int deleted = notificationService.purgeOlderThan(Instant.now().minus(NOTIFICATION_RETENTION));
        if (deleted > 0) {
            log.info("cleanup_notifications deleted={}", deleted);
        }
    }

    public void cleanActivity() {
        int deleted = activityService.purgeOlderThan(Instant.now().minus(ACTIVITY_RETENTION));
        if (deleted > 0) {
            log.info("cleanup_activity deleted={}", deleted);
        }
    }

    @Transactional
    public void cleanWebhookDeliveries() {
        int deleted = webhookDeliveryRepository.deleteOlderThan(Instant.now().minus(WEBHOOK_RETENTION));
        if (deleted > 0) {
            log.info("cleanup_webhook_deliveries deleted={}", deleted);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void safely(String phase, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("cleanup_phase_failed phase={} reason={}", phase, e.getMessage());
        }
    }

    private boolean isStopped(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("exited") || normalized.contains("dead") || normalized.contains("created");
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
