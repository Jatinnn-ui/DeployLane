package com.deployforge.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per pipeline stage, which is what the deployment timeline renders.
 *
 * <p>Derived from logs it would be guesswork; stored explicitly it gives exact per-stage durations
 * ("Application built - 31 seconds") and an unambiguous failure point.
 *
 * <p>Deliberately not extending the shared audited base class: a step has no meaningful "created"
 * moment, it has {@code startedAt} and {@code finishedAt}. Inheriting a {@code created_at} column would
 * mean carrying a field that duplicates {@code startedAt} and means nothing while the step is pending.
 */
@Entity
@Table(
        name = "deployment_steps",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_deployment_step",
                        columnNames = {"deployment_id", "step"}))
public class DeploymentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "deployment_id", nullable = false)
    private UUID deploymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 64)
    private DeploymentStepName step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StepStatus status;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected DeploymentStep() {}

    public DeploymentStep(UUID deploymentId, DeploymentStepName step, int sequenceNumber) {
        this.deploymentId = deploymentId;
        this.step = step;
        this.sequenceNumber = sequenceNumber;
        this.status = StepStatus.PENDING;
    }

    public void start() {
        this.status = StepStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void succeed(String detail) {
        finish(StepStatus.SUCCEEDED, detail);
    }

    public void fail(String detail) {
        finish(StepStatus.FAILED, detail);
    }

    public void skip(String detail) {
        this.status = StepStatus.SKIPPED;
        this.detail = truncate(detail);
        this.finishedAt = Instant.now();
        this.durationMs = 0L;
    }

    public void cancel() {
        finish(StepStatus.CANCELLED, "Cancelled by user");
    }

    private void finish(StepStatus finalStatus, String detail) {
        this.status = finalStatus;
        this.detail = truncate(detail);
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 1997) + "...";
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public DeploymentStepName getStep() {
        return step;
    }

    public StepStatus getStatus() {
        return status;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public enum StepStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED,
        CANCELLED
    }
}
