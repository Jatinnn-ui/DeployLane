package com.deployforge.environment;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A deployment target inside a project: a branch, a set of encrypted variables and at most one live
 * container.
 */
@Entity
@Table(
        name = "environments",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_environment_name",
                        columnNames = {"project_id", "name"}))
public class Environment extends AuditedEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private EnvironmentType type;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "auto_deploy_enabled", nullable = false)
    private boolean autoDeployEnabled;

    protected Environment() {}

    public Environment(UUID projectId, String name, EnvironmentType type, String branch, boolean autoDeployEnabled) {
        this.projectId = projectId;
        this.name = name;
        this.type = type;
        this.branch = branch;
        this.autoDeployEnabled = autoDeployEnabled;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EnvironmentType getType() {
        return type;
    }

    public void setType(EnvironmentType type) {
        this.type = type;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public boolean isAutoDeployEnabled() {
        return autoDeployEnabled;
    }

    public void setAutoDeployEnabled(boolean autoDeployEnabled) {
        this.autoDeployEnabled = autoDeployEnabled;
    }
}
