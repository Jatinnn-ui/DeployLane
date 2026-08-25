package com.deployforge.deployment.event;

import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Real-time events pushed to subscribed browsers over {@code /ws/deployments/{id}}.
 *
 * <p>Every event carries an explicit {@code type} field so the frontend can switch on it without
 * relying on structural guessing, and every event is self contained so a client that missed earlier
 * messages still renders correctly.
 */
public sealed interface DeploymentEvent {

    String type();

    UUID deploymentId();

    Instant timestamp();

    record Log(
            String type,
            UUID deploymentId,
            Instant timestamp,
            long sequence,
            LogLevel level,
            LogSource source,
            String message)
            implements DeploymentEvent {

        public Log(
                UUID deploymentId,
                Instant timestamp,
                long sequence,
                LogLevel level,
                LogSource source,
                String message) {
            this("LOG", deploymentId, timestamp, sequence, level, source, message);
        }
    }

    record StatusChanged(
            String type,
            UUID deploymentId,
            Instant timestamp,
            String status,
            String statusLabel,
            Integer deploymentNumber)
            implements DeploymentEvent {

        public StatusChanged(
                UUID deploymentId, String status, String statusLabel, Integer deploymentNumber) {
            this("STATUS_CHANGED", deploymentId, Instant.now(), status, statusLabel, deploymentNumber);
        }
    }

    record StepUpdated(
            String type,
            UUID deploymentId,
            Instant timestamp,
            String step,
            String stepLabel,
            String status,
            Long durationMs,
            String detail)
            implements DeploymentEvent {

        public StepUpdated(
                UUID deploymentId,
                String step,
                String stepLabel,
                String status,
                Long durationMs,
                String detail) {
            this("STEP_UPDATED", deploymentId, Instant.now(), step, stepLabel, status, durationMs, detail);
        }
    }

    record DeploymentReady(
            String type, UUID deploymentId, Instant timestamp, String url, Long durationMs)
            implements DeploymentEvent {

        public DeploymentReady(UUID deploymentId, String url, Long durationMs) {
            this("DEPLOYMENT_READY", deploymentId, Instant.now(), url, durationMs);
        }
    }

    record DeploymentFailed(
            String type,
            UUID deploymentId,
            Instant timestamp,
            String stage,
            String message,
            Integer exitCode)
            implements DeploymentEvent {

        public DeploymentFailed(UUID deploymentId, String stage, String message, Integer exitCode) {
            this("DEPLOYMENT_FAILED", deploymentId, Instant.now(), stage, message, exitCode);
        }
    }

    record AnalysisReady(String type, UUID deploymentId, Instant timestamp, String severity, Double confidence)
            implements DeploymentEvent {

        public AnalysisReady(UUID deploymentId, String severity, Double confidence) {
            this("ANALYSIS_READY", deploymentId, Instant.now(), severity, confidence);
        }
    }

    record Metrics(
            String type,
            UUID deploymentId,
            Instant timestamp,
            Double cpuPercent,
            Long memoryBytes,
            Long memoryLimitBytes,
            Integer restartCount,
            String containerStatus)
            implements DeploymentEvent {

        public Metrics(
                UUID deploymentId,
                Double cpuPercent,
                Long memoryBytes,
                Long memoryLimitBytes,
                Integer restartCount,
                String containerStatus) {
            this(
                    "METRICS",
                    deploymentId,
                    Instant.now(),
                    cpuPercent,
                    memoryBytes,
                    memoryLimitBytes,
                    restartCount,
                    containerStatus);
        }
    }
}
