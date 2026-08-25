package com.deployforge.monitoring.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Monitoring payloads. */
public final class MonitoringDtos {

    private MonitoringDtos() {}

    /** Health of a project's live environment. */
    public record ProjectHealthResponse(
            UUID projectId,
            String status,
            UUID environmentId,
            String environmentName,
            UUID deploymentId,
            Integer deploymentNumber,
            String deploymentStatus,
            String url,
            String commitSha,
            Instant deployedAt,
            Long uptimeSeconds,
            String containerStatus,
            Integer restartCount,
            Double cpuPercent,
            Long memoryBytes,
            Long memoryLimitBytes,
            Instant lastSampledAt,
            String detail) {

        public static ProjectHealthResponse noDeployment(UUID projectId, String detail) {
            return new ProjectHealthResponse(
                    projectId, "NO_DEPLOYMENT", null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, detail);
        }
    }

    public record MetricSample(
            Instant sampledAt,
            Double cpuPercent,
            Long memoryBytes,
            Long memoryLimitBytes,
            Integer restartCount,
            String containerStatus) {}

    public record DeploymentMetricsResponse(
            UUID deploymentId,
            String containerStatus,
            Double cpuPercent,
            Long memoryBytes,
            Long memoryLimitBytes,
            Integer restartCount,
            Instant startedAt,
            Long uptimeSeconds,
            List<MetricSample> history,
            boolean live) {}
}
