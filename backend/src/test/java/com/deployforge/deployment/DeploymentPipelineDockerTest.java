package com.deployforge.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.deployment.pipeline.DeploymentContext;
import com.deployforge.deployment.pipeline.DeploymentPipeline;
import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.environment.EnvironmentType;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectRepository;
import com.deployforge.runtime.DeploymentRuntime;
import com.deployforge.security.EncryptionService;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.workspace.Workspace;
import com.deployforge.workspace.WorkspaceMember;
import com.deployforge.workspace.WorkspaceMemberRepository;
import com.deployforge.workspace.WorkspaceRepository;
import com.deployforge.workspace.WorkspaceRole;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real deployment pipeline end to end against the local Docker engine.
 *
 * <p>This is the test that proves the product works rather than compiles: a git repository is cloned,
 * the framework is detected from the checkout, a Dockerfile is generated, an image is built, a container
 * is started with resource limits, it is health checked over HTTP, and traffic is promoted to it.
 *
 * <p>The repository is a local git repository created from {@code examples/demo-node-app}, so the test
 * needs no GitHub credentials. Everything after cloning is exactly the production code path.
 *
 * <p>Tagged {@code docker}: needs a running engine and network access to pull {@code node:20-alpine}.
 * Run with {@code mvn test -Dtest=DeploymentPipelineDockerTest -DexcludedGroups=}.
 */
@Tag("docker")
@Testcontainers
@EnabledOnOs({OS.WINDOWS, OS.LINUX, OS.MAC})
@SpringBootTest
class DeploymentPipelineDockerTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPipelineDockerTest.class);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("deployforge")
                    .withUsername("deployforge")
                    .withPassword("deployforge");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    private static Path sourceRepository;
    private static Path buildRoot;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("deployforge.security.jwt-secret", () -> "pipeline-test-jwt-secret-value-at-least-32-bytes");
        registry.add("deployforge.security.encryption-key", EncryptionService::generateKeyBase64);
        registry.add("deployforge.deployment.root-path", () -> buildRoot.toString());
        registry.add("deployforge.deployment.docker-host-uri", DeploymentPipelineDockerTest::dockerHost);
        // A container that cannot answer in 90s is broken, not slow.
        registry.add("deployforge.deployment.health-check-timeout", () -> "90s");
        registry.add("deployforge.deployment.build-timeout", () -> "600s");
        // A dedicated port window so the test cannot collide with a developer's running deployments.
        registry.add("deployforge.deployment.port-range-start", () -> 31500);
        registry.add("deployforge.deployment.port-range-end", () -> 31599);
    }

    private static String dockerHost() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? "npipe:////./pipe/docker_engine"
                : "unix:///var/run/docker.sock";
    }

    @BeforeAll
    static void prepareRepository() throws Exception {
        buildRoot = Files.createTempDirectory("deployforge-pipeline-builds");
        sourceRepository = Files.createTempDirectory("deployforge-demo-repo");

        // The example app lives in the repository; copy it and make it a real git repository so the
        // clone step exercises JGit rather than being stubbed out.
        Path example = Path.of("..", "examples", "demo-node-app").toAbsolutePath().normalize();
        assertThat(Files.isDirectory(example))
                .as("examples/demo-node-app must exist next to the backend module")
                .isTrue();

        try (Stream<Path> files = Files.list(example)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.copy(
                        file,
                        sourceRepository.resolve(file.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try (Git git = Git.init().setDirectory(sourceRepository.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("Add demo node app")
                    .setAuthor("DeployForge Test", "test@deployforge.local")
                    .setSign(false)
                    .call();
        }
        log.info("prepared test repository at {}", sourceRepository);
    }

    @AfterAll
    static void cleanupDirectories() {
        deleteRecursively(sourceRepository);
        deleteRecursively(buildRoot);
    }

    @Autowired DeploymentPipeline pipeline;
    @Autowired DeploymentService deploymentService;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentStepRepository stepRepository;
    @Autowired DeploymentRuntime runtime;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired EnvironmentRepository environmentRepository;
    @Autowired EnvironmentVariableService variableService;
    @Autowired DeploymentLogService logService;

    @Test
    @DisplayName("clones, builds an image, starts a container, health checks it and promotes it")
    void deploysEndToEnd() throws Exception {
        // --- arrange: a project whose "repository" is the local git repository -------------------
        User user = userRepository.save(new User(9001L, "pipeline-tester", "Pipeline Tester", null, null));
        Workspace workspace =
                workspaceRepository.save(new Workspace("Pipeline workspace", "pipeline-ws", user.getId()));
        memberRepository.save(new WorkspaceMember(workspace.getId(), user.getId(), WorkspaceRole.OWNER));

        Project project =
                new Project(
                        workspace.getId(),
                        "Demo Node App",
                        "demo-node-" + UUID.randomUUID().toString().substring(0, 6),
                        "deployforge",
                        "demo-node-app",
                        sourceRepository.toUri().toString(),
                        null,
                        false,
                        "main",
                        user.getId());
        project.setFramework(Framework.NODE);
        project.setRuntime(RuntimeType.NODE);
        project.setContainerPort(3000);
        project.setHealthCheckPath("/healthz");
        project = projectRepository.save(project);

        Environment environment =
                environmentRepository.save(
                        new Environment(project.getId(), "production", EnvironmentType.PRODUCTION, "main", false));

        // A variable, so injection and log redaction are exercised on a real container.
        variableService.upsert(environment.getId(), "DEMO_TOKEN", "super-secret-demo-token-value");

        Deployment deployment =
                deploymentService.persistQueuedDeployment(
                        project, environment, "main", DeploymentTriggerType.MANUAL, user.getId(), null, null);

        // The production factory derives an https GitHub URL; this test clones the local repository
        // instead, which is the only substitution made. Every step after the clone is unchanged.
        DeploymentContext context =
                new DeploymentContext(
                        deployment.getId(),
                        project.getId(),
                        environment.getId(),
                        workspace.getId(),
                        deployment.getDeploymentNumber(),
                        DeploymentTriggerType.MANUAL,
                        project.getSlug(),
                        project.getName(),
                        project.getRepositoryOwner(),
                        project.getRepositoryName(),
                        sourceRepository.toUri().toString(),
                        false,
                        "main",
                        () -> false);
        context.setFramework(project.getFramework());
        context.setRuntime(project.getRuntime());
        context.setContainerPort(3000);
        context.setHealthCheckPath("/healthz");

        // --- act ---------------------------------------------------------------------------------
        pipeline.run(context);

        // --- assert: the deployment is live ------------------------------------------------------
        Deployment result = deploymentRepository.findById(deployment.getId()).orElseThrow();
        List<DeploymentStep> steps =
                stepRepository.findByDeploymentIdOrderBySequenceNumberAsc(deployment.getId());

        if (result.getStatus() != DeploymentStatus.READY) {
            // Surface the real reason instead of a bare assertion failure.
            log.error(
                    "deployment did not reach READY: status={} stage={} message={}",
                    result.getStatus(),
                    result.getFailureStage(),
                    result.getFailureMessage());
            logService.tailPlain(deployment.getId(), 80).forEach(line -> log.error("  {}", line));
        }

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.READY);
        assertThat(result.getCommitSha()).isNotBlank();
        assertThat(result.getCommitMessage()).isEqualTo("Add demo node app");
        assertThat(result.getImageTag()).isEqualTo("deployforge/" + project.getId() + ":1");
        assertThat(result.getContainerId()).isNotBlank();
        assertThat(result.getHostPort()).isBetween(31500, 31599);
        assertThat(result.getDeploymentUrl()).contains(String.valueOf(result.getHostPort()));
        assertThat(result.isPromoted()).isTrue();
        assertThat(result.getDurationMs()).isNotNull().isPositive();

        // The whole pipeline ran, in order, and every step succeeded.
        assertThat(steps)
                .extracting(step -> step.getStep().name())
                .containsExactly(
                        "QUEUE", "CLONE", "DETECT", "BUILD", "IMAGE_BUILD", "CONTAINER_START",
                        "HEALTH_CHECK", "FINALIZE");
        assertThat(steps)
                .allSatisfy(
                        step ->
                                assertThat(step.getStatus())
                                        .as(step.getStep().name())
                                        .isEqualTo(DeploymentStep.StepStatus.SUCCEEDED));

        // --- assert: the deployed application actually answers ----------------------------------
        HttpResponse<String> response =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()
                        .send(
                                HttpRequest.newBuilder(URI.create(result.getDeploymentUrl() + "/healthz"))
                                        .timeout(Duration.ofSeconds(10))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"ok\"");

        // --- assert: the container is hardened and the secret never reached the log --------------
        var stats = runtime.getStats(result.getContainerId()).orElseThrow();
        assertThat(stats.status()).isEqualTo("running");
        assertThat(stats.memoryLimitBytes()).isPositive();

        // Container output is followed on a background thread and flushed in batches, so wait for it
        // rather than assuming it has landed by the time the pipeline returns.
        List<String> logs = awaitApplicationLogs(deployment.getId(), "DEMO_TOKEN");
        assertThat(logs).isNotEmpty();
        // The app prints the variable names it received; DeployForge must have captured that...
        assertThat(String.join("\n", logs)).contains("DEMO_TOKEN");
        // ...without the value ever reaching storage.
        assertThat(logs).noneMatch(line -> line.contains("super-secret-demo-token-value"));

        // --- cleanup: this test created real infrastructure -------------------------------------
        runtime.stop(result.getContainerId(), Duration.ofSeconds(5));
        runtime.remove(result.getContainerId());
        runtime.removeImage(result.getImageTag());
    }

    /**
     * Polls until a specific line from the container's own output has been ingested.
     *
     * <p>Waits for the exact line under assertion rather than "any application line": output is flushed in
     * batches, so an earlier batch arriving proves nothing about a later one.
     */
    private List<String> awaitApplicationLogs(UUID deploymentId, String needle)
            throws InterruptedException {
        List<String> logs = List.of();
        for (int attempt = 0; attempt < 40; attempt++) {
            logs = logService.tailPlain(deploymentId, 400);
            if (logs.stream().anyMatch(line -> line.contains(needle))) {
                return logs;
            }
            Thread.sleep(500);
        }
        return logs;
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            entry -> {
                                try {
                                    Files.deleteIfExists(entry);
                                } catch (IOException ignored) {
                                    // Temporary directory; a leftover file is not worth failing the build.
                                }
                            });
        } catch (IOException ignored) {
            // Same.
        }
    }
}
