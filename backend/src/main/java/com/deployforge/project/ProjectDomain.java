package com.deployforge.project;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A hostname (or local port mapping) that routes traffic to an environment's active container.
 *
 * <p>On a single node local install these are {@code localhost:<port>} entries produced by
 * {@code LocalPortDomainRoutingService}. With a reverse proxy in front they become real hostnames.
 * The entity is shared by both so switching routing strategies does not change the data model.
 */
@Entity
@Table(name = "project_domains")
public class ProjectDomain extends AuditedEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "hostname", nullable = false, unique = true)
    private String hostname;

    @Column(name = "target_port")
    private Integer targetPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private DomainKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DomainStatus status;

    protected ProjectDomain() {}

    public ProjectDomain(
            UUID projectId,
            UUID environmentId,
            String hostname,
            Integer targetPort,
            DomainKind kind,
            DomainStatus status) {
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.hostname = hostname;
        this.targetPort = targetPort;
        this.kind = kind;
        this.status = status;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public String getHostname() {
        return hostname;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public DomainKind getKind() {
        return kind;
    }

    public DomainStatus getStatus() {
        return status;
    }

    public void setStatus(DomainStatus status) {
        this.status = status;
    }

    public enum DomainKind {
        /** Auto generated, e.g. a published host port or {@code <slug>.<base domain>}. */
        SYSTEM,
        /** User supplied custom domain. */
        CUSTOM
    }

    public enum DomainStatus {
        ACTIVE,
        PENDING,
        DISABLED
    }
}
