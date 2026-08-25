package com.deployforge.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.common.util.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Detection drives the build, so a wrong answer here produces a confusing failure much later. Each case
 * pins a realistic repository shape to the framework, commands and port it should produce.
 */
class FrameworkDetectionServiceTest {

    private final FrameworkDetectionService detectionService =
            new FrameworkDetectionService(new JsonCodec(new ObjectMapper()));

    @Test
    @DisplayName("a committed Dockerfile wins over everything else and its EXPOSE is used")
    void detectsDockerfile() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("Dockerfile", "FROM node:20\nEXPOSE 4000\nCMD [\"node\", \"index.js\"]")
                        .with("package.json", packageJson("{\"next\":\"14.0.0\"}", "{}"));

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.DOCKER);
        assertThat(result.runtime()).isEqualTo(RuntimeType.DOCKER);
        assertThat(result.port()).isEqualTo(4000);
        assertThat(result.dockerfilePath()).isEqualTo("Dockerfile");
        assertThat(result.confidence()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("Next.js is detected from its dependency and gets a server runtime")
    void detectsNextJs() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "package.json",
                                packageJson("{\"next\":\"14.1.0\",\"react\":\"18.2.0\"}",
                                        "{\"build\":\"next build\",\"start\":\"next start\"}"))
                        .with("package-lock.json", "{}");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.NEXTJS);
        assertThat(result.runtime()).isEqualTo(RuntimeType.NODE);
        assertThat(result.installCommand()).isEqualTo("npm ci");
        assertThat(result.buildCommand()).isEqualTo("npm run build");
        assertThat(result.startCommand()).isEqualTo("npm start");
        assertThat(result.port()).isEqualTo(3000);
        assertThat(result.framework().isStaticOutput()).isFalse();
    }

    @Test
    @DisplayName("Vite + React is static output served on port 80 with no start command")
    void detectsViteReact() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "package.json",
                                packageJson(
                                        "{\"react\":\"18.2.0\",\"vite\":\"5.0.0\"}",
                                        "{\"build\":\"tsc && vite build\",\"dev\":\"vite\"}"))
                        .with("package-lock.json", "{}");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.REACT_VITE);
        assertThat(result.framework().isStaticOutput()).isTrue();
        assertThat(result.startCommand()).isNull();
        assertThat(result.port()).isEqualTo(80);
        assertThat(result.framework().staticOutputDirectory()).isEqualTo("dist");
    }

    @Test
    @DisplayName("a missing lockfile downgrades npm ci to npm install")
    void withoutLockfileUsesNpmInstall() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "package.json",
                                packageJson("{\"express\":\"4.18.2\"}", "{\"start\":\"node server.js\"}"));

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.EXPRESS);
        assertThat(result.installCommand()).isEqualTo("npm install");
    }

    @Test
    @DisplayName("pnpm is detected from its lockfile and used for install and build")
    void detectsPnpm() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "package.json",
                                packageJson("{\"next\":\"14.0.0\"}", "{\"build\":\"next build\"}"))
                        .with("pnpm-lock.yaml", "lockfileVersion: 9");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.packageManager()).isEqualTo(PackageManager.PNPM);
        assertThat(result.installCommand()).isEqualTo("pnpm install --frozen-lockfile");
        assertThat(result.buildCommand()).isEqualTo("pnpm run build");
    }

    @Test
    @DisplayName("NestJS is preferred over the generic Node fallback")
    void detectsNestJs() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "package.json",
                                packageJson(
                                        "{\"@nestjs/core\":\"10.0.0\",\"express\":\"4.18.2\"}",
                                        "{\"build\":\"nest build\",\"start:prod\":\"node dist/main\"}"));

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.NESTJS);
    }

    @Test
    @DisplayName("Spring Boot is detected from pom.xml")
    void detectsSpringBootMaven() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with(
                                "pom.xml",
                                "<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent></project>");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.SPRING_BOOT_MAVEN);
        assertThat(result.runtime()).isEqualTo(RuntimeType.JAVA);
        assertThat(result.buildCommand()).contains("package");
        assertThat(result.port()).isEqualTo(8080);
        assertThat(result.confidence()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("a Gradle wrapper is preferred over a system gradle")
    void detectsGradleWrapper() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("build.gradle", "plugins { id 'org.springframework.boot' version '3.2.0' }")
                        .with("gradlew", "#!/bin/sh");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.SPRING_BOOT_GRADLE);
        assertThat(result.buildCommand()).isEqualTo("./gradlew build -x test");
    }

    @Test
    @DisplayName("FastAPI is detected and its entrypoint is inferred")
    void detectsFastApi() {
        InMemoryInspector inspector =
                new InMemoryInspector().with("requirements.txt", "fastapi==0.110.0\nuvicorn==0.29.0");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.FASTAPI);
        assertThat(result.runtime()).isEqualTo(RuntimeType.PYTHON);
        assertThat(result.startCommand()).contains("uvicorn main:app");
        assertThat(result.startCommand()).contains("0.0.0.0");
        assertThat(result.port()).isEqualTo(8000);
    }

    @Test
    @DisplayName("an app/main.py layout changes the inferred FastAPI module")
    void detectsFastApiInAppPackage() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("requirements.txt", "fastapi\nuvicorn")
                        .with("app/main.py", "app = FastAPI()");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.startCommand()).contains("app.main:app");
    }

    @Test
    @DisplayName("Django and Flask are distinguished")
    void detectsDjangoAndFlask() {
        assertThat(
                        detectionService
                                .detect(new InMemoryInspector().with("requirements.txt", "Django==5.0"), null)
                                .framework())
                .isEqualTo(Framework.DJANGO);
        assertThat(
                        detectionService
                                .detect(new InMemoryInspector().with("requirements.txt", "Flask==3.0\ngunicorn"), null)
                                .framework())
                .isEqualTo(Framework.FLASK);
    }

    @Test
    @DisplayName("a bare index.html is a static site")
    void detectsStaticSite() {
        InMemoryInspector inspector =
                new InMemoryInspector().with("index.html", "<!doctype html><title>hi</title>");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.framework()).isEqualTo(Framework.STATIC);
        assertThat(result.port()).isEqualTo(80);
    }

    @Test
    @DisplayName("an unrecognised repository returns UNKNOWN with an explanation, not a guess")
    void returnsUnknown() {
        FrameworkDetectionResult result =
                detectionService.detect(new InMemoryInspector().with("README.md", "# hello"), null);

        assertThat(result.framework()).isEqualTo(Framework.UNKNOWN);
        assertThat(result.confidence()).isZero();
        assertThat(result.requiresConfirmation()).isTrue();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("a monorepo root directory scopes detection to that package")
    void respectsRootDirectory() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("package.json", packageJson("{}", "{}"))
                        .with(
                                "apps/web/package.json",
                                packageJson("{\"next\":\"14.0.0\"}", "{\"build\":\"next build\"}"))
                        .with("apps/web/package-lock.json", "{}");

        FrameworkDetectionResult result = detectionService.detect(inspector, "apps/web");

        assertThat(result.framework()).isEqualTo(Framework.NEXTJS);
        assertThat(result.installCommand()).isEqualTo("npm ci");
    }

    @Test
    @DisplayName("a static framework without a build script is flagged as a warning")
    void warnsAboutMissingBuildScript() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("package.json", packageJson("{\"react\":\"18.2.0\",\"vite\":\"5.0.0\"}", "{}"));

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("evidence explains the decision")
    void producesEvidence() {
        InMemoryInspector inspector =
                new InMemoryInspector()
                        .with("package.json", packageJson("{\"next\":\"14.0.0\"}", "{\"build\":\"next build\"}"))
                        .with("package-lock.json", "{}");

        FrameworkDetectionResult result = detectionService.detect(inspector, null);

        assertThat(result.evidence()).anyMatch(line -> line.contains("next"));
        assertThat(result.evidence()).anyMatch(line -> line.contains("package-lock.json"));
    }

    private String packageJson(String dependencies, String scripts) {
        return "{\"name\":\"app\",\"dependencies\":"
                + dependencies
                + ",\"devDependencies\":{},\"scripts\":"
                + scripts
                + "}";
    }

    /** Minimal in-memory repository, so detection is tested without GitHub or a filesystem. */
    private static final class InMemoryInspector implements SourceInspector {

        private final Map<String, String> files = new LinkedHashMap<>();

        InMemoryInspector with(String path, String content) {
            files.put(path, content);
            return this;
        }

        @Override
        public Optional<String> readFile(String relativePath) {
            return Optional.ofNullable(files.get(normalize(relativePath)));
        }

        @Override
        public boolean exists(String relativePath) {
            String normalized = normalize(relativePath);
            return files.containsKey(normalized)
                    || files.keySet().stream().anyMatch(path -> path.startsWith(normalized + "/"));
        }

        @Override
        public List<String> listDirectory(String relativeDirectory) {
            String prefix = normalize(relativeDirectory);
            String search = prefix.isEmpty() ? "" : prefix + "/";
            List<String> entries = new ArrayList<>();
            for (String path : files.keySet()) {
                if (!path.startsWith(search)) {
                    continue;
                }
                String remainder = path.substring(search.length());
                int slash = remainder.indexOf('/');
                entries.add(slash < 0 ? remainder : remainder.substring(0, slash));
            }
            return entries.stream().distinct().toList();
        }

        @Override
        public String describe() {
            return "in-memory";
        }

        private String normalize(String path) {
            if (path == null) {
                return "";
            }
            String cleaned = path;
            while (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1);
            }
            while (cleaned.endsWith("/")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return cleaned;
        }
    }
}
