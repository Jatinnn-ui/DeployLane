package com.deployforge.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One resource usage sample for a running deployment.
 *
 * <p>Sampled on an interval (30s by default) rather than per second: this is a deployment platform, not a
 * time series database. Retention is enforced by the cleanup job, so the table stays bounded.
 */
@Entity
@Table(name = "deployment_metrics")
public class DeploymentMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private UUID deploymentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "cpu_percent")
    private Double cpuPercent;

    @Column(name = "memory_bytes")
    private Long memoryBytes;

    @Column(name = "memory_limit_bytes")
    private Long memoryLimitBytes;

    @Column(name = "restart_count")
    private Integer restartCount;

    @Column(name = "container_status", length = 32)
    private String containerStatus;

    protected DeploymentMetric() {}

    public DeploymentMetric(
            UUID deploymentId,
            UUID projectId,
            Instant sampledAt,
            Double cpuPercent,
            Long memoryBytes,
            Long memoryLimitBytes,
            Integer restartCount,
            String containerStatus) {
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.sampledAt = sampledAt;
        this.cpuPercent = cpuPercent;
        this.memoryBytes = memoryBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.restartCount = restartCount;
        this.containerStatus = containerStatus;
    }

    public Long getId() {
        return id;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    public Double getCpuPercent() {
        return cpuPercent;
    }

    public Long getMemoryBytes() {
        return memoryBytes;
    }

    public Long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public Integer getRestartCount() {
        return restartCount;
    }

    public String getContainerStatus() {
        return containerStatus;
    }
}
