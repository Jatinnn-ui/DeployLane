package com.deployforge.deployment;

import com.deployforge.common.jpa.AuditedEntity;
import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to ship a commit to an environment.
 *
 * <p>Deployments are immutable history: configuration is <em>snapshotted</em> here at creation time
 * (framework, commands, port, root directory) so a deployment from three weeks ago still shows what it
 * actually ran, and a rollback can reuse an image without guessing. Nothing rewrites a past
 * deployment; a rollback or redeploy always creates a new row.
 */
@Entity
@Table(
        name = "deployments",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_deployment_number",
                        columnNames = {"project_id", "deployment_number"}))
public class Deployment extends AuditedEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "deployment_number", nullable = false)
    private int deploymentNumber;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "commit_message", length = 2000)
    private String commitMessage;

    @Column(name = "commit_author")
    private String commitAuthor;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private DeploymentTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeploymentStatus status = DeploymentStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "framework", length = 64)
    private Framework framework;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime", length = 64)
    private RuntimeType runtime;

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

    /** Present when DeployForge generated the Dockerfile; kept as reviewable build metadata. */
    @Column(name = "generated_dockerfile", columnDefinition = "text")
    private String generatedDockerfile;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "image_tag", length = 512)
    private String imageTag;

    @Column(name = "container_id", length = 128)
    private String containerId;

    @Column(name = "deployment_url", length = 1024)
    private String deploymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 32)
    private DeploymentStatus failureStage;

    @Column(name = "failure_message", length = 4000)
    private String failureMessage;

    @Column(name = "exit_code")
    private Integer exitCode;

    /** For ROLLBACK / REDEPLOY: the deployment this one was derived from. */
    @Column(name = "source_deployment_id")
    private UUID sourceDeploymentId;

    /** True once this deployment owns the environment's traffic. */
    @Column(name = "promoted", nullable = false)
    private boolean promoted;

    @Column(name = "cancellation_requested", nullable = false)
    private boolean cancellationRequested;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Deployment() {}

    public Deployment(
            UUID projectId,
            UUID environmentId,
            int deploymentNumber,
            String branch,
            DeploymentTriggerType triggerType,
            UUID createdBy) {
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.deploymentNumber = deploymentNumber;
        this.branch = branch;
        this.triggerType = triggerType;
        this.createdBy = createdBy;
        this.status = DeploymentStatus.QUEUED;
        this.queuedAt = Instant.now();
    }

    /**
     * Applies a status change. Only {@link DeploymentStateMachine} should call this, which is why it
     * also maintains the timing fields rather than leaving that to callers.
     */
    void applyStatus(DeploymentStatus target) {
        this.status = target;
        if (target == DeploymentStatus.CLONING && startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (target.isTerminal() && finishedAt == null) {
            this.finishedAt = Instant.now();
            Instant from = startedAt != null ? startedAt : queuedAt;
            if (from != null) {
                this.durationMs = Duration.between(from, finishedAt).toMillis();
            }
        }
    }

    public void markFailure(DeploymentStatus stage, String message, Integer exitCode) {
        this.failureStage = stage;
        this.failureMessage = message == null ? null : truncate(message, 4000);
        if (exitCode != null) {
            this.exitCode = exitCode;
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    public String shortCommitSha() {
        if (commitSha == null) {
            return null;
        }
        return commitSha.substring(0, Math.min(7, commitSha.length()));
    }

    /** True when the built image can be reused for a rollback. */
    public boolean hasReusableImage() {
        return imageTag != null && !imageTag.isBlank();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public int getDeploymentNumber() {
        return deploymentNumber;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage == null ? null : truncate(commitMessage, 2000);
    }

    public String getCommitAuthor() {
        return commitAuthor;
    }

    public void setCommitAuthor(String commitAuthor) {
        this.commitAuthor = commitAuthor;
    }

    public String getBranch() {
        return branch;
    }

    public DeploymentTriggerType getTriggerType() {
        return triggerType;
    }

    public DeploymentStatus getStatus() {
        return status;
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

    public String getGeneratedDockerfile() {
        return generatedDockerfile;
    }

    public void setGeneratedDockerfile(String generatedDockerfile) {
        this.generatedDockerfile = generatedDockerfile;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getDeploymentUrl() {
        return deploymentUrl;
    }

    public void setDeploymentUrl(String deploymentUrl) {
        this.deploymentUrl = deploymentUrl;
    }

    public DeploymentStatus getFailureStage() {
        return failureStage;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public UUID getSourceDeploymentId() {
        return sourceDeploymentId;
    }

    public void setSourceDeploymentId(UUID sourceDeploymentId) {
        this.sourceDeploymentId = sourceDeploymentId;
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public void setCancellationRequested(boolean cancellationRequested) {
        this.cancellationRequested = cancellationRequested;
    }

    public Instant getQueuedAt() {
        return queuedAt;
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

    public UUID getCreatedBy() {
        return createdBy;
    }
}
