package com.deployforge.deployment.pipeline;

import com.deployforge.detection.Framework;
import com.deployforge.detection.FrameworkDetectionResult;
import com.deployforge.detection.RuntimeType;
import com.deployforge.deployment.DeploymentTriggerType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Mutable state shared by the pipeline steps for a single deployment.
 *
 * <p>Holds plain values rather than JPA entities on purpose. The pipeline runs for minutes and must
 * not keep a persistence context or a database connection open across a clone or an image build;
 * persistence happens in short, explicit transactions through {@code DeploymentStateService}.
 *
 * <p>The GitHub token lives here for the duration of the clone only, and is cleared afterwards.
 */
public class DeploymentContext {

    // ---- identity -----------------------------------------------------------
    private final UUID deploymentId;
    private final UUID projectId;
    private final UUID environmentId;
    private final UUID workspaceId;
    private final int deploymentNumber;
    private final DeploymentTriggerType triggerType;
    private final Instant startedAt = Instant.now();

    // ---- source -------------------------------------------------------------
    private final String projectSlug;
    private final String projectName;
    private final String repositoryOwner;
    private final String repositoryName;
    private final String cloneUrl;
    private final boolean repositoryPrivate;
    private final String branch;
    private String githubToken;

    // ---- configuration snapshot --------------------------------------------
    private Framework framework;
    private RuntimeType runtime;
    private String rootDirectory;
    private String installCommand;
    private String buildCommand;
    private String startCommand;
    private String dockerfilePath;
    private int containerPort;
    private String healthCheckPath;

    // ---- working state ------------------------------------------------------
    private Path workDirectory;
    private Path sourceDirectory;
    private Path dockerfile;
    private String generatedDockerfile;
    private FrameworkDetectionResult detection;
    private final Map<String, String> environmentVariables = new LinkedHashMap<>();

    private String commitSha;
    private String commitMessage;
    private String commitAuthor;

    private String imageTag;
    private String containerId;
    private Integer hostPort;
    private String deploymentUrl;

    /** Set for rollbacks: the image to reuse instead of cloning and building again. */
    private String reusableImageTag;

    private final BooleanSupplier cancellationCheck;

    public DeploymentContext(
            UUID deploymentId,
            UUID projectId,
            UUID environmentId,
            UUID workspaceId,
            int deploymentNumber,
            DeploymentTriggerType triggerType,
            String projectSlug,
            String projectName,
            String repositoryOwner,
            String repositoryName,
            String cloneUrl,
            boolean repositoryPrivate,
            String branch,
            BooleanSupplier cancellationCheck) {
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.workspaceId = workspaceId;
        this.deploymentNumber = deploymentNumber;
        this.triggerType = triggerType;
        this.projectSlug = projectSlug;
        this.projectName = projectName;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.cloneUrl = cloneUrl;
        this.repositoryPrivate = repositoryPrivate;
        this.branch = branch;
        this.cancellationCheck = cancellationCheck;
    }

    public boolean isCancelled() {
        return cancellationCheck != null && cancellationCheck.getAsBoolean();
    }

    /** Rollbacks reuse a previously built image and skip clone, detect and build. */
    public boolean isImageReuse() {
        return reusableImageTag != null && !reusableImageTag.isBlank();
    }

    public String containerName() {
        return "df-" + projectSlug + "-" + deploymentNumber;
    }

    /** Deterministic, per project image repository so cleanup and rollback can find images. */
    public String imageRepository() {
        return "deployforge/" + projectId;
    }

    public String buildImageTag() {
        return imageRepository() + ":" + deploymentNumber;
    }

    // ---- getters / setters --------------------------------------------------

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public int getDeploymentNumber() {
        return deploymentNumber;
    }

    public DeploymentTriggerType getTriggerType() {
        return triggerType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String repositoryFullName() {
        return repositoryOwner + "/" + repositoryName;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public boolean isRepositoryPrivate() {
        return repositoryPrivate;
    }

    public String getBranch() {
        return branch;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    /** Called as soon as the clone finishes; the token must not outlive the step that needs it. */
    public void clearGithubToken() {
        this.githubToken = null;
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

    public int getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(int containerPort) {
        this.containerPort = containerPort;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public Path getWorkDirectory() {
        return workDirectory;
    }

    public void setWorkDirectory(Path workDirectory) {
        this.workDirectory = workDirectory;
    }

    public Path getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(Path sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public Path getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(Path dockerfile) {
        this.dockerfile = dockerfile;
    }

    public String getGeneratedDockerfile() {
        return generatedDockerfile;
    }

    public void setGeneratedDockerfile(String generatedDockerfile) {
        this.generatedDockerfile = generatedDockerfile;
    }

    public FrameworkDetectionResult getDetection() {
        return detection;
    }

    public void setDetection(FrameworkDetectionResult detection) {
        this.detection = detection;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void putEnvironmentVariables(Map<String, String> values) {
        environmentVariables.putAll(values);
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
        this.commitMessage = commitMessage;
    }

    public String getCommitAuthor() {
        return commitAuthor;
    }

    public void setCommitAuthor(String commitAuthor) {
        this.commitAuthor = commitAuthor;
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

    public Integer getHostPort() {
        return hostPort;
    }

    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public String getDeploymentUrl() {
        return deploymentUrl;
    }

    public void setDeploymentUrl(String deploymentUrl) {
        this.deploymentUrl = deploymentUrl;
    }

    public String getReusableImageTag() {
        return reusableImageTag;
    }

    public void setReusableImageTag(String reusableImageTag) {
        this.reusableImageTag = reusableImageTag;
    }
}
