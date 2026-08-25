package com.deployforge.deployment.pipeline.steps;

import com.deployforge.common.util.SafePaths;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.detection.Framework;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.docker.DockerfileGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Decides how the image will be built and writes the build plan into the working copy.
 *
 * <p>Two paths:
 *
 * <ol>
 *   <li><b>The repository ships a Dockerfile.</b> It is used verbatim - DeployForge does not second
 *       guess it, does not rewrite it, and does not inject anything into it.
 *   <li><b>No Dockerfile.</b> One is generated from the detected framework and written into the
 *       <em>temporary</em> checkout (never into the user's repository), and kept as build metadata so
 *       the exact recipe used is reviewable afterwards.
 * </ol>
 */
@Component
public class BuildPlanStep implements PipelineStep {

    private final DockerfileGenerator generator;
    private final DeploymentLogService logService;
    private final DeploymentStateService stateService;

    public BuildPlanStep(
            DockerfileGenerator generator,
            DeploymentLogService logService,
            DeploymentStateService stateService) {
        this.generator = generator;
        this.logService = logService;
        this.stateService = stateService;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.BUILD;
    }

    @Override
    public boolean applies(DeploymentContext context) {
        return !context.isImageReuse();
    }

    @Override
    public String skipReason(DeploymentContext context) {
        return "Image already built by the source deployment";
    }

    @Override
    public void execute(DeploymentContext context) {
        requireSourceCheckout(context);
        Path source = context.getSourceDirectory();

        if (StringUtils.hasText(context.getDockerfilePath())) {
            useCommittedDockerfile(context, source);
        } else {
            Path candidate = SafePaths.resolveInside(source, dockerfileCandidate(context));
            if (Files.isRegularFile(candidate)) {
                context.setDockerfilePath(dockerfileCandidate(context));
                useCommittedDockerfile(context, source);
            } else {
                generateDockerfile(context, source);
            }
        }

        ensureDockerignore(context, source);
        logPlan(context);
    }

    private void requireSourceCheckout(DeploymentContext context) {
        if (context.getSourceDirectory() == null) {
            throw new StepFailedException("No source checkout is available to build");
        }
    }

    private String dockerfileCandidate(DeploymentContext context) {
        String prefix =
                StringUtils.hasText(context.getRootDirectory()) ? context.getRootDirectory() + "/" : "";
        return prefix + "Dockerfile";
    }

    private void useCommittedDockerfile(DeploymentContext context, Path source) {
        Path dockerfile = SafePaths.resolveInside(source, context.getDockerfilePath());
        if (!Files.isRegularFile(dockerfile)) {
            throw new StepFailedException(
                    "Dockerfile not found at '" + context.getDockerfilePath() + "'",
                    "Correct the Dockerfile path in project settings, or remove it to let DeployForge generate one.");
        }
        context.setDockerfile(dockerfile);
        context.setFramework(Framework.DOCKER);
        context.setRuntime(com.deployforge.detection.RuntimeType.DOCKER);
        stateService.recordDockerfile(context.getDeploymentId(), context.getDockerfilePath(), null);
        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.SYSTEM,
                "Using the Dockerfile committed at " + context.getDockerfilePath());
    }

    private void generateDockerfile(DeploymentContext context, Path source) {
        String contextSubdirectory =
                StringUtils.hasText(context.getRootDirectory()) ? context.getRootDirectory() : ".";

        DockerfileGenerator.GeneratedBuildFiles generated;
        try {
            generated =
                    generator.generate(
                            new DockerfileGenerator.Spec(
                                    context.getFramework(),
                                    context.getRuntime(),
                                    contextSubdirectory,
                                    context.getInstallCommand(),
                                    context.getBuildCommand(),
                                    context.getStartCommand(),
                                    context.getDetection() == null
                                            ? null
                                            : context.getFramework().staticOutputDirectory(),
                                    context.getContainerPort()));
        } catch (IllegalStateException e) {
            throw new StepFailedException(
                    "DeployForge cannot generate a Dockerfile for " + context.getFramework().displayName(),
                    "Commit a Dockerfile to the repository and redeploy.");
        }

        Path dockerfile = SafePaths.resolveInside(source, generated.dockerfileName());
        try {
            Files.writeString(dockerfile, generated.dockerfile(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> auxiliary : generated.auxiliaryFiles().entrySet()) {
                Path auxiliaryPath = SafePaths.resolveInside(source, auxiliary.getKey());
                Files.writeString(auxiliaryPath, auxiliary.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new StepFailedException(
                    "Could not write the generated build files: " + e.getMessage(), null, null, e);
        }

        context.setDockerfile(dockerfile);
        context.setGeneratedDockerfile(generated.dockerfile());
        stateService.recordDockerfile(
                context.getDeploymentId(),
                generated.dockerfileName() + " (generated)",
                generated.dockerfile());

        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.SYSTEM,
                "No Dockerfile committed - generated one for " + context.getFramework().displayName());
    }

    /**
     * Writes a conservative ignore file only when the repository has none.
     *
     * <p>Without it, {@code node_modules} and {@code .git} get packed into the build context, which on a
     * real project means hundreds of megabytes shipped to the daemon for nothing.
     */
    private void ensureDockerignore(DeploymentContext context, Path source) {
        Path dockerignore = SafePaths.resolveInside(source, ".dockerignore");
        if (Files.exists(dockerignore)) {
            return;
        }
        try {
            Files.writeString(dockerignore, generator.defaultDockerignore(), StandardCharsets.UTF_8);
            logService.append(
                    context.getDeploymentId(),
                    LogLevel.DEBUG,
                    LogSource.SYSTEM,
                    "Added a default .dockerignore to the build context");
        } catch (IOException e) {
            // Not fatal: the build still works, it is just slower.
            logService.append(
                    context.getDeploymentId(),
                    LogLevel.WARN,
                    LogSource.SYSTEM,
                    "Could not write .dockerignore: " + e.getMessage());
        }
    }

    private void logPlan(DeploymentContext context) {
        StringBuilder plan = new StringBuilder("Build plan: ");
        plan.append("framework=").append(context.getFramework().displayName());
        if (StringUtils.hasText(context.getInstallCommand())) {
            plan.append(", install='").append(context.getInstallCommand()).append('\'');
        }
        if (StringUtils.hasText(context.getBuildCommand())) {
            plan.append(", build='").append(context.getBuildCommand()).append('\'');
        }
        if (StringUtils.hasText(context.getStartCommand())) {
            plan.append(", start='").append(context.getStartCommand()).append('\'');
        }
        plan.append(", port=").append(context.getContainerPort());
        logService.append(context.getDeploymentId(), LogLevel.INFO, LogSource.SYSTEM, plan.toString());
    }
}
