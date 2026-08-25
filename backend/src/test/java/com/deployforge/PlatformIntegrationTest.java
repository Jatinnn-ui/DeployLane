package com.deployforge;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentRepository;
import com.deployforge.deployment.DeploymentStatus;
import com.deployforge.deployment.DeploymentTriggerType;
import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.environment.EnvironmentType;
import com.deployforge.environment.EnvironmentVariableRepository;
import com.deployforge.environment.EnvironmentVariableService;
import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectRepository;
import com.deployforge.security.EncryptionService;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.workspace.Workspace;
import com.deployforge.workspace.WorkspaceMember;
import com.deployforge.workspace.WorkspaceMemberRepository;
import com.deployforge.workspace.WorkspaceRepository;
import com.deployforge.workspace.WorkspaceRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application against a real PostgreSQL and Redis.
 *
 * <p>The most valuable assertion here is the one that happens implicitly: the context only starts if Flyway's
 * migrations and every JPA mapping agree, because Hibernate runs with {@code ddl-auto=validate}. A column
 * renamed in an entity but not in a migration fails this test rather than production.
 *
 * <p>Tagged {@code docker} and excluded from the default build, so {@code mvn test} stays green on a machine
 * without a container runtime. Run it with {@code mvn test -Dgroups=docker}.
 */
@Tag("docker")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformIntegrationTest {

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

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Required configuration: the application deliberately refuses to start without these.
        registry.add("deployforge.security.jwt-secret", () -> "integration-test-jwt-secret-value-at-least-32-bytes");
        registry.add("deployforge.security.encryption-key", EncryptionService::generateKeyBase64);
        registry.add("deployforge.deployment.root-path", () -> "target/test-builds");
        // No Docker engine is needed for these assertions; the runtime client is created lazily.
        registry.add("deployforge.deployment.docker-host-uri", () -> "unix:///var/run/docker.sock");
    }

    @LocalServerPort int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired EnvironmentRepository environmentRepository;
    @Autowired EnvironmentVariableRepository variableRepository;
    @Autowired EnvironmentVariableService variableService;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentLogService logService;
    @Autowired EncryptionService encryptionService;

    @Test
    @DisplayName("the context starts, which proves the Flyway schema and the JPA mappings agree")
    void contextStarts() {
        assertThat(userRepository).isNotNull();
        assertThat(port).isPositive();
    }

    @Test
    @DisplayName("the health endpoint is public and reports each dependency")
    void healthEndpointReportsComponents() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/api/v1/health",
                        org.springframework.http.HttpMethod.GET,
                        null,
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

        // 200 when everything is up, 503 when Docker is unavailable in CI - both are valid answers.
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).containsKeys("database", "redis", "docker", "buildStorage");

        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) components.get("database");
        assertThat(database.get("up")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) components.get("redis");
        assertThat(redis.get("up")).isEqualTo(true);
    }

    @Test
    @DisplayName("protected endpoints reject anonymous callers with the structured error envelope")
    void protectedEndpointsRequireAuthentication() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/api/v1/projects",
                        org.springframework.http.HttpMethod.GET,
                        null,
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsKey("code");
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("the full aggregate persists, and a variable is stored only as ciphertext")
    void persistsTheDomainAndEncryptsVariables() {
        User user =
                userRepository.save(
                        new User(4242L, "octocat", "The Octocat", "octocat@example.com", null));
        Workspace workspace =
                workspaceRepository.save(new Workspace("Octocat workspace", "octocat-ws", user.getId()));
        memberRepository.save(new WorkspaceMember(workspace.getId(), user.getId(), WorkspaceRole.OWNER));

        Project project =
                new Project(
                        workspace.getId(),
                        "Maya AI",
                        "maya-ai",
                        "octocat",
                        "maya-ai",
                        "https://github.com/octocat/maya-ai",
                        99L,
                        false,
                        "main",
                        user.getId());
        project.setFramework(Framework.NEXTJS);
        project.setRuntime(RuntimeType.NODE);
        project.setContainerPort(3000);
        project.setBuildCommand("npm run build");
        project.setStartCommand("npm start");
        project = projectRepository.save(project);

        Environment environment =
                environmentRepository.save(
                        new Environment(
                                project.getId(), "production", EnvironmentType.PRODUCTION, "main", true));

        variableService.upsert(environment.getId(), "DATABASE_URL", "postgres://user:secret@db:5432/app");

        // The stored form must be ciphertext, and must not contain the plaintext anywhere.
        var stored =
                variableRepository
                        .findByEnvironmentIdAndKey(environment.getId(), "DATABASE_URL")
                        .orElseThrow();
        assertThat(stored.getEncryptedValue()).startsWith(EncryptionService.VERSION_PREFIX);
        assertThat(stored.getEncryptedValue()).doesNotContain("secret");
        assertThat(encryptionService.decrypt(stored.getEncryptedValue()))
                .isEqualTo("postgres://user:secret@db:5432/app");

        // Reads expose names only.
        assertThat(variableService.keyNames(environment.getId())).containsExactly("DATABASE_URL");

        Deployment deployment =
                deploymentRepository.save(
                        new Deployment(
                                project.getId(),
                                environment.getId(),
                                1,
                                "main",
                                DeploymentTriggerType.MANUAL,
                                user.getId()));
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(deploymentRepository.findMaxDeploymentNumber(project.getId())).isEqualTo(1);

        logService.registerSecrets(deployment.getId(), java.util.List.of("secret"));
        logService.append(
                deployment.getId(),
                LogLevel.ERROR,
                LogSource.APPLICATION,
                "connecting to postgres://user:secret@db:5432/app failed");
        logService.clearSecrets(deployment.getId());

        var logs = logService.tail(deployment.getId(), 10);
        assertThat(logs).hasSize(1);
        // Redaction happens on write, so the plaintext never reached the table.
        assertThat(logs.get(0).message()).doesNotContain("secret");
        assertThat(logs.get(0).message()).contains("[REDACTED_SECRET]");
        assertThat(logs.get(0).sequence()).isEqualTo(1L);
    }
}
