package com.deployforge.project;

import com.deployforge.common.jpa.AuditedEntity;
import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A deployable application, bound to exactly one GitHub repository.
 *
 * <p>The build configuration stored here is the project default. Every deployment snapshots these
 * values at creation time so that a historic deployment always shows the configuration it actually
 * ran with, even after someone edits the project.
 */
@Entity
@Table(
        name = "projects",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_project_slug",
                        columnNames = {"workspace_id", "slug"}))
public class Project extends AuditedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "repository_owner", nullable = false)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "repository_url", nullable = false, length = 1024)
    private String repositoryUrl;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "repository_private", nullable = false)
    private boolean repositoryPrivate;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Enumerated(EnumType.STRING)
    @Column(name = "framework", length = 64)
    private Framework framework;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime", length = 64)
    private RuntimeType runtime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "root_directory", length = 512)
    private String rootDirectory;

    @Column(name = "install_command", length = 1024)
    private String installCommand;

    @Column(name = "build_command", length = 1024)
    private String buildCommand;

    @Column(name = "start_command", length = 1024)
    private String startCommand;

    @Column(name = "dockerfile_path", length = 512)
    private String dockerfilePath;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "health_check_path", length = 512)
    private String healthCheckPath;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Project() {}

    public Project(
            UUID workspaceId,
            String name,
            String slug,
            String repositoryOwner,
            String repositoryName,
            String repositoryUrl,
            Long repositoryId,
            boolean repositoryPrivate,
            String defaultBranch,
            UUID createdBy) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.slug = slug;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.repositoryUrl = repositoryUrl;
        this.repositoryId = repositoryId;
        this.repositoryPrivate = repositoryPrivate;
        this.defaultBranch = defaultBranch;
        this.createdBy = createdBy;
        this.status = ProjectStatus.ACTIVE;
    }

    public String repositoryFullName() {
        return repositoryOwner + "/" + repositoryName;
    }

    public boolean isDeployable() {
        return status == ProjectStatus.ACTIVE;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public boolean isRepositoryPrivate() {
        return repositoryPrivate;
    }

    public void setRepositoryPrivate(boolean repositoryPrivate) {
        this.repositoryPrivate = repositoryPrivate;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public Framework getFramework() {
        return framework;
    }

    public void setFramework(Framework framework) {
        this.framework = framework;
    }

    public RuntimeType getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeType runtime) {
        this.runtime = runtime;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public String getInstallCommand() {
        return installCommand;
    }

    public void setInstallCommand(String installCommand) {
        this.installCommand = installCommand;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    public void setBuildCommand(String buildCommand) {
        this.buildCommand = buildCommand;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public void setStartCommand(String startCommand) {
        this.startCommand = startCommand;
    }

    public String getDockerfilePath() {
        return dockerfilePath;
    }

    public void setDockerfilePath(String dockerfilePath) {
        this.dockerfilePath = dockerfilePath;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
