package com.deployforge.ai;

import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.monitoring.MonitoringService;
import com.deployforge.monitoring.dto.MonitoringDtos.ProjectHealthResponse;
import com.deployforge.project.Project;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The controlled set of "tools" the DevOps assistant may use.
 *
 * <p>This is the security boundary of the chat feature. The model never queries anything: the backend
 * decides which of these read-only tools are relevant, executes them itself, and passes the rendered text
 * as context. There is no tool that mutates state, and there is deliberately no tool that executes a shell
 * command or reads a secret value.
 *
 * <p>Tool selection is keyword driven and therefore predictable and cheap - and the selected tool names are
 * returned to the UI so the user can see exactly what the answer was based on.
 */
@Component
public class AiToolRegistry {

    private static final int RECENT_DEPLOYMENTS = 8;
    private static final int LOG_LINES = 60;

    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableService variableService;
    private final DeploymentLogService logService;
    private final MonitoringService monitoringService;
    private final DeploymentAnalysisService analysisService;

    public AiToolRegistry(
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            EnvironmentVariableService variableService,
            DeploymentLogService logService,
            MonitoringService monitoringService,
            DeploymentAnalysisService analysisService) {
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.variableService = variableService;
        this.logService = logService;
        this.monitoringService = monitoringService;
        this.analysisService = analysisService;
    }

    /** Names of every available tool, exposed so the UI can explain the feature honestly. */
    public List<String> availableTools() {
        return List.of(
                "getProjectSummary",
                "getLatestDeployment",
                "getRecentDeployments",
                "getFailedDeployments",
                "getDeploymentLogs",
                "getDeploymentAnalysis",
                "getProjectHealth",
                "getContainerStats",
                "getEnvironmentVariableNames");
    }

    /**
     * Chooses tools for a question and renders their output.
     *
     * @param focusDeploymentId optional deployment the user is looking at
     */
    @Transactional(readOnly = true)
    public ToolContext gather(Project project, String question, UUID focusDeploymentId) {
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Set<String> used = new LinkedHashSet<>();
        StringBuilder context = new StringBuilder();

        used.add("getProjectSummary");
        context.append(projectSummary(project)).append('\n');

        Optional<Deployment> focus =
                focusDeploymentId == null
                        ? deploymentRepository.findFirstByProjectIdOrderByDeploymentNumberDesc(project.getId())
                        : deploymentRepository.findById(focusDeploymentId);

        if (focus.isPresent()) {
            used.add("getLatestDeployment");
            context.append(deploymentDetail(focus.get())).append('\n');
        }

        boolean wantsHistory =
                containsAny(lower, "history", "this week", "recent", "last deployments", "compare", "previous");
        boolean wantsFailures = containsAny(lower, "fail", "error", "broke", "crash", "why");
        boolean wantsLogs = containsAny(lower, "log", "output", "stack", "trace", "error", "npm", "exception");
        boolean wantsHealth =
                containsAny(lower, "health", "healthy", "up", "down", "running", "uptime", "restart");
        boolean wantsStats =
                containsAny(lower, "memory", "cpu", "resource", "usage", "slow", "performance", "ram");
        boolean wantsVariables = containsAny(lower, "environment variable", "env var", "config", "secret");

        if (wantsHistory) {
            used.add("getRecentDeployments");
            context.append(recentDeployments(project)).append('\n');
        }
        if (wantsFailures) {
            used.add("getFailedDeployments");
            context.append(failedDeployments(project)).append('\n');
        }
        if (focus.isPresent() && wantsLogs) {
            used.add("getDeploymentLogs");
            context.append(deploymentLogs(focus.get())).append('\n');
        }
        if (focus.isPresent() && (wantsFailures || wantsLogs)) {
            used.add("getDeploymentAnalysis");
            context.append(deploymentAnalysis(focus.get())).append('\n');
        }
        if (wantsHealth || wantsStats) {
            used.add("getProjectHealth");
            context.append(projectHealth(project)).append('\n');
        }
        if (wantsStats) {
            used.add("getContainerStats");
            context.append(containerStats(project)).append('\n');
        }
        if (wantsVariables) {
            used.add("getEnvironmentVariableNames");
            context.append(environmentVariableNames(project)).append('\n');
        }

        return new ToolContext(context.toString(), List.copyOf(used));
    }

    // ------------------------------------------------------------------ tools

    private String projectSummary(Project project) {
        StringBuilder text = new StringBuilder("[project]\n");
        text.append("name: ").append(project.getName()).append('\n');
        text.append("repository: ").append(project.repositoryFullName()).append('\n');
        text.append("framework: ")
                .append(project.getFramework() == null ? "not set" : project.getFramework().displayName())
                .append('\n');
        text.append("status: ").append(project.getStatus()).append('\n');
        text.append("port: ").append(project.getContainerPort()).append('\n');
        if (project.getBuildCommand() != null) {
            text.append("build command: ").append(project.getBuildCommand()).append('\n');
        }
        if (project.getStartCommand() != null) {
            text.append("start command: ").append(project.getStartCommand()).append('\n');
        }
        List<Environment> environments =
                environmentRepository.findByProjectIdOrderByTypeAscNameAsc(project.getId());
        for (Environment environment : environments) {
            text.append("environment: ")
                    .append(environment.getName())
                    .append(" (")
                    .append(environment.getType())
                    .append(", branch ")
                    .append(environment.getBranch())
                    .append(", auto deploy ")
                    .append(environment.isAutoDeployEnabled() ? "on" : "off")
                    .append(")\n");
        }
        return text.toString();
    }

    private String deploymentDetail(Deployment deployment) {
        StringBuilder text = new StringBuilder("[deployment #" + deployment.getDeploymentNumber() + "]\n");
        text.append("status: ").append(deployment.getStatus()).append('\n');
        text.append("trigger: ").append(deployment.getTriggerType()).append('\n');
        text.append("branch: ").append(deployment.getBranch()).append('\n');
        if (deployment.getCommitSha() != null) {
            text.append("commit: ")
                    .append(deployment.shortCommitSha())
                    .append(" ")
                    .append(deployment.getCommitMessage() == null ? "" : deployment.getCommitMessage())
                    .append('\n');
        }
        if (deployment.getFailureStage() != null) {
            text.append("failed at stage: ").append(deployment.getFailureStage()).append('\n');
        }
        if (deployment.getFailureMessage() != null) {
            text.append("failure message: ").append(deployment.getFailureMessage()).append('\n');
        }
        if (deployment.getExitCode() != null) {
            text.append("exit code: ").append(deployment.getExitCode()).append('\n');
        }
        if (deployment.getDurationMs() != null) {
            text.append("duration: ").append(deployment.getDurationMs() / 1000).append("s\n");
        }
        if (deployment.getDeploymentUrl() != null) {
            text.append("url: ").append(deployment.getDeploymentUrl()).append('\n');
        }
        return text.toString();
    }

    private String recentDeployments(Project project) {
        List<Deployment> deployments =
                deploymentRepository
                        .findByProjectIdOrderByDeploymentNumberDesc(
                                project.getId(), PageRequest.of(0, RECENT_DEPLOYMENTS))
                        .getContent();
        StringBuilder text = new StringBuilder("[recent deployments]\n");
        for (Deployment deployment : deployments) {
            text.append("#")
                    .append(deployment.getDeploymentNumber())
                    .append(" ")
                    .append(deployment.getStatus())
                    .append(" ")
                    .append(deployment.getTriggerType())
                    .append(" ")
                    .append(deployment.getBranch())
                    .append(" ")
                    .append(deployment.shortCommitSha() == null ? "" : deployment.shortCommitSha())
                    .append(" at ")
                    .append(deployment.getCreatedAt())
                    .append(deployment.getDurationMs() == null
                            ? ""
                            : " (" + deployment.getDurationMs() / 1000 + "s)")
                    .append('\n');
        }
        return text.toString();
    }

    private String failedDeployments(Project project) {
        List<Deployment> failures =
                deploymentRepository.findRecentFailures(List.of(project.getId()), PageRequest.of(0, 5));
        StringBuilder text = new StringBuilder("[recent failures]\n");
        if (failures.isEmpty()) {
            text.append("none in the stored history\n");
            return text.toString();
        }
        for (Deployment failure : failures) {
            text.append("#")
                    .append(failure.getDeploymentNumber())
                    .append(" stage=")
                    .append(failure.getFailureStage())
                    .append(" message=")
                    .append(failure.getFailureMessage())
                    .append(" at ")
                    .append(failure.getCreatedAt())
                    .append('\n');
        }
        return text.toString();
    }

    private String deploymentLogs(Deployment deployment) {
        List<String> lines = logService.tailPlain(deployment.getId(), LOG_LINES);
        StringBuilder text =
                new StringBuilder("[logs for deployment #" + deployment.getDeploymentNumber() + "]\n");
        if (lines.isEmpty()) {
            text.append("no log lines stored\n");
            return text.toString();
        }
        lines.forEach(line -> text.append(line).append('\n'));
        return text.toString();
    }

    private String deploymentAnalysis(Deployment deployment) {
        return analysisService
                .findForDeployment(deployment.getId())
                .map(
                        analysis ->
                                "[existing analysis for #"
                                        + deployment.getDeploymentNumber()
                                        + "]\nstatus: "
                                        + analysis.getStatus()
                                        + "\nprovider: "
                                        + analysis.getProvider()
                                        + "\nroot cause: "
                                        + analysis.getRootCause()
                                        + "\nconfidence: "
                                        + analysis.getConfidence()
                                        + "\n")
                .orElse("[existing analysis]\nnone stored for this deployment\n");
    }

    private String projectHealth(Project project) {
        ProjectHealthResponse health = monitoringService.projectHealth(project.getId());
        return "[health]\nstatus: "
                + health.status()
                + "\ncontainer: "
                + health.containerStatus()
                + "\ndeployment: "
                + health.deploymentNumber()
                + "\nuptime seconds: "
                + health.uptimeSeconds()
                + "\nrestarts: "
                + health.restartCount()
                + "\ndetail: "
                + health.detail()
                + "\n";
    }

    private String containerStats(Project project) {
        ProjectHealthResponse health = monitoringService.projectHealth(project.getId());
        if (health.deploymentId() == null) {
            return "[container stats]\nno live container\n";
        }
        var metrics = monitoringService.deploymentMetrics(health.deploymentId(), Duration.ofMinutes(30));
        StringBuilder text = new StringBuilder("[container stats]\n");
        text.append("cpu percent: ").append(metrics.cpuPercent()).append('\n');
        text.append("memory bytes: ").append(metrics.memoryBytes()).append('\n');
        text.append("memory limit bytes: ").append(metrics.memoryLimitBytes()).append('\n');
        text.append("restarts: ").append(metrics.restartCount()).append('\n');
        text.append("samples in the last 30 minutes: ").append(metrics.history().size()).append('\n');
        if (!metrics.history().isEmpty()) {
            double peak =
                    metrics.history().stream()
                            .mapToDouble(sample -> sample.cpuPercent() == null ? 0 : sample.cpuPercent())
                            .max()
                            .orElse(0);
            long peakMemory =
                    metrics.history().stream()
                            .mapToLong(sample -> sample.memoryBytes() == null ? 0 : sample.memoryBytes())
                            .max()
                            .orElse(0);
            text.append("peak cpu percent: ").append(peak).append('\n');
            text.append("peak memory bytes: ").append(peakMemory).append('\n');
        }
        return text.toString();
    }

    /** Names only. There is no tool anywhere in this class that can read a value. */
    private String environmentVariableNames(Project project) {
        StringBuilder text = new StringBuilder("[environment variable names, values withheld]\n");
        for (Environment environment :
                environmentRepository.findByProjectIdOrderByTypeAscNameAsc(project.getId())) {
            List<String> names = variableService.keyNames(environment.getId());
            text.append(environment.getName()).append(": ");
            text.append(names.isEmpty() ? "(none)" : String.join(", ", names));
            text.append('\n');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ helpers

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Rendered context plus the tools that produced it. */
    public record ToolContext(String rendered, List<String> usedTools) {}

    /** Convenience used by the quick action buttons in the AI sidebar. */
    @Transactional(readOnly = true)
    public List<Deployment> lastTwoDeployments(UUID projectId) {
        List<Deployment> deployments = new ArrayList<>();
        deploymentRepository
                .findByProjectIdOrderByDeploymentNumberDesc(projectId, PageRequest.of(0, 2))
                .forEach(deployments::add);
        return deployments;
    }

    @Transactional(readOnly = true)
    public boolean hasLiveDeployment(UUID projectId) {
        return deploymentRepository
                .findByProjectIdAndStatusIn(projectId, List.of(DeploymentStatus.READY))
                .stream()
                .anyMatch(deployment -> deployment.getContainerId() != null);
    }

    /** Used to describe how fresh the context is. */
    public String describeGatheredAt() {
        return "context gathered at " + Instant.now();
    }
}
