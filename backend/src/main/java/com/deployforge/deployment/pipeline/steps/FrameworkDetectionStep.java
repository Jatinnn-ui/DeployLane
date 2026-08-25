package com.deployforge.deployment.pipeline.steps;

import com.deployforge.common.util.SafePaths;
import com.deployforge.deployment.DeploymentStateService;
import com.deployforge.deployment.DeploymentStepName;
import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.PipelineStep;
import com.deployforge.deployment.pipeline.StepFailedException;
import com.deployforge.detection.FileSystemSourceInspector;
import com.deployforge.detection.Framework;
import com.deployforge.detection.FrameworkDetectionResult;
import com.deployforge.detection.FrameworkDetectionService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Re-runs framework detection against the actual checkout.
 *
 * <p>Detection also ran at import time against the GitHub API, but the branch has moved on since then:
 * a dependency may have been added, a Dockerfile committed, a lockfile switched from npm to pnpm.
 * Re-detecting here keeps the build honest, while explicit user configuration always wins over
 * anything detected.
 */
@Component
public class FrameworkDetectionStep implements PipelineStep {

    private final FrameworkDetectionService detectionService;
    private final DeploymentLogService logService;
    private final DeploymentStateService stateService;

    public FrameworkDetectionStep(
            FrameworkDetectionService detectionService,
            DeploymentLogService logService,
            DeploymentStateService stateService) {
        this.detectionService = detectionService;
        this.logService = logService;
        this.stateService = stateService;
    }

    @Override
    public DeploymentStepName name() {
        return DeploymentStepName.DETECT;
    }

    @Override
    public boolean applies(DeploymentContext context) {
        return !context.isImageReuse();
    }

    @Override
    public String skipReason(DeploymentContext context) {
        return "Configuration reused from the source deployment";
    }

    @Override
    public void execute(DeploymentContext context) {
        Path sourceDirectory = context.getSourceDirectory();
        if (sourceDirectory == null || !Files.isDirectory(sourceDirectory)) {
            throw new StepFailedException("The cloned source directory is missing");
        }
        if (StringUtils.hasText(context.getRootDirectory())) {
            Path appDirectory = SafePaths.resolveInside(sourceDirectory, context.getRootDirectory());
            if (!Files.isDirectory(appDirectory)) {
                throw new StepFailedException(
                        "Root directory '" + context.getRootDirectory() + "' does not exist in this branch",
                        "Update the root directory in project settings to match the repository layout.");
            }
        }

        FrameworkDetectionResult detection =
                detectionService.detect(
                        new FileSystemSourceInspector(sourceDirectory), context.getRootDirectory());
        context.setDetection(detection);

        for (String evidence : detection.evidence()) {
            logService.append(context.getDeploymentId(), LogLevel.INFO, LogSource.SYSTEM, "  - " + evidence);
        }
        for (String warning : detection.warnings()) {
            logService.append(context.getDeploymentId(), LogLevel.WARN, LogSource.SYSTEM, warning);
        }

        // Configuration precedence: explicit project settings, then detection defaults.
        if (context.getFramework() == null || context.getFramework() == Framework.UNKNOWN) {
            context.setFramework(detection.framework());
            context.setRuntime(detection.runtime());
        }
        if (!StringUtils.hasText(context.getInstallCommand())) {
            context.setInstallCommand(detection.installCommand());
        }
        if (!StringUtils.hasText(context.getBuildCommand())) {
            context.setBuildCommand(detection.buildCommand());
        }
        if (!StringUtils.hasText(context.getStartCommand())) {
            context.setStartCommand(detection.startCommand());
        }
        if (context.getContainerPort() <= 0) {
            context.setContainerPort(detection.port());
        }
        if (!StringUtils.hasText(context.getDockerfilePath())
                && StringUtils.hasText(detection.dockerfilePath())) {
            context.setDockerfilePath(detection.dockerfilePath());
            context.setFramework(Framework.DOCKER);
            context.setRuntime(com.deployforge.detection.RuntimeType.DOCKER);
        }

        if (context.getFramework() == null || context.getFramework() == Framework.UNKNOWN) {
            throw new StepFailedException(
                    "DeployForge could not determine how to build this project",
                    "Add a Dockerfile, or set the framework, build command and start command in project settings.");
        }

        stateService.recordDetection(
                context.getDeploymentId(),
                context.getFramework(),
                context.getRuntime(),
                context.getInstallCommand(),
                context.getBuildCommand(),
                context.getStartCommand(),
                context.getContainerPort());

        logService.append(
                context.getDeploymentId(),
                LogLevel.INFO,
                LogSource.SYSTEM,
                "Framework: "
                        + context.getFramework().displayName()
                        + " ("
                        + Math.round(detection.confidence() * 100)
                        + "% confidence), port "
                        + context.getContainerPort());
    }
}
