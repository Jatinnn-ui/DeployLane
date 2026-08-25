package com.deployforge.deployment.pipeline.steps;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineLogSink;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepCancelledException;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.runtime.RuntimeExceptions.BuildFailedException;
import com.deployforge.runtime.RuntimeExceptions.OperationCancelledException;
import com.deployforge.runtime.RuntimeExceptions.OperationTimedOutException;
import com.deployforge.runtime.RuntimeExceptions.RuntimeUnavailableException;
import com.deployforge.runtime.RuntimeModels.BuildRequest;
import com.deployforge.runtime.RuntimeModels.BuildResult;
import com.deployforge.runtime.docker.DockerLabels;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the application image.
 *
 * <p>This is where dependency installation and the application build actually run - inside the image,
 * not on the host. That is the point: the build is reproducible, isolated from the platform, and the
 * result is the artefact that gets shipped rather than something assembled next to it.
 *
 * <p>Output streams into the deployment log while the build runs, the build is time boxed, and the
 * three interesting failure modes (build error, timeout, engine unavailable) are reported distinctly.
 */
@Component
public class ImageBuildStep implements PipelineStep {

    private final DeploymentRuntime runtime;
    private final DeploymentLogService logService;
    private final DeploymentStateService stateService;
    private final DeployForgeProperties properties;

    public ImageBuildStep(
            DeploymentRuntime runtime,
            DeploymentLogService logService,
            DeploymentStateService stateService,
            DeployForgeProperties properties) {
        this.runtime = runtime;
        this.logService = logService;
        this.stateService = stateService;
        this.properties = properties;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.IMAGE_BUILD;
    }

    @Override
    public boolean applies(DeploymentContext context) {
        return !context.isImageReuse();
    }

    @Override
    public String skipReason(DeploymentContext context) {
        return "Reusing image " + context.getReusableImageTag();
    }

    @Override
    public void execute(DeploymentContext context) {
        String imageTag = context.buildImageTag();
        Map<String, String> labels =
                DockerLabels.forDeployment(
                        context.getProjectId(),
                        context.getProjectSlug(),
                        context.getEnvironmentId(),
                        context.getDeploymentId(),
                        context.getDeploymentNumber(),
                        context.getCommitSha());

        PipelineLogSink sink =
                new PipelineLogSink(
                        logService, context.getDeploymentId(), LogSource.DOCKER, context::isCancelled, true);

        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.DOCKER,
                "Building image " + imageTag);

        try {
            BuildResult result =
                    runtime.build(
                            new BuildRequest(
                                    imageTag,
                                    context.getSourceDirectory(),
                                    context.getDockerfile(),
                                    Map.of(),
                                    labels,
                                    properties.deployment().buildTimeout(),
                                    sink));

            context.setImageTag(result.imageTag());
            stateService.recordImage(context.getDeploymentId(), result.imageTag());
            sink.flush();

            logService.append(
                    context.getDeploymentId(),
                    LogLevel.INFO,
                    LogSource.DOCKER,
                    "Image built in " + formatDuration(result.durationMs()));

        } catch (OperationCancelledException e) {
            sink.flush();
            throw new StepCancelledException("Image build cancelled");
        } catch (OperationTimedOutException e) {
            sink.flush();
            throw new StepFailedException(
                    e.getMessage(),
                    "Reduce build work, add a .dockerignore, or raise DEPLOYMENT_BUILD_TIMEOUT_SECONDS.");
        } catch (BuildFailedException e) {
            sink.flush();
            throw new StepFailedException(
                    e.getMessage(),
                    "Read the build output above - the failing command is the last one before the error.",
                    e.getExitCode());
        } catch (RuntimeUnavailableException e) {
            sink.flush();
            throw new StepFailedException(
                    "The Docker engine is unavailable: " + e.getMessage(),
                    "Start Docker and check DOCKER_HOST_URI, then redeploy.");
        } finally {
            sink.flush();
        }
    }

    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
