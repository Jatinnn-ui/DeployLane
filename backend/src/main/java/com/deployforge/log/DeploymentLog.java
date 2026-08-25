package com.deployforge.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A single deployment log line.
 *
 * <p>{@code sequenceNumber} is a per-deployment monotonic counter and doubles as the pagination
 * cursor: it is stable while new lines are still streaming in, which an offset never is. The message
 * has already passed through secret redaction before it gets here.
 */
@Entity
@Table(
        name = "deployment_logs",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_deployment_log_sequence",
                        columnNames = {"deployment_id", "sequence_number"}))
public class DeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private UUID deploymentId;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    private LogLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private LogSource source;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    protected DeploymentLog() {}

    public DeploymentLog(
            UUID deploymentId,
            Instant loggedAt,
            LogLevel level,
            LogSource source,
            String message,
            long sequenceNumber) {
        this.deploymentId = deploymentId;
        this.loggedAt = loggedAt;
        this.level = level;
        this.source = source;
        this.message = message;
        this.sequenceNumber = sequenceNumber;
    }

    public Long getId() {
        return id;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public LogLevel getLevel() {
        return level;
    }

    public LogSource getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }
}
