package com.deployforge.runtime.docker;

import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Produces a Dockerfile for projects that do not ship one.
 *
 * <p>Written into the temporary build context, never into the user's repository. The generated content
 * is stored as build metadata so a deployment can always be reproduced and reviewed.
 *
 * <p>Conventions applied:
 *
 * <ul>
 *   <li>multi-stage builds for static sites and JVM apps, so the runtime image carries no toolchain
 *   <li>static output is served by nginx with an SPA fallback, which is what a client side router needs
 *   <li>{@code NODE_ENV=production} is set only after the build, otherwise devDependencies needed to
 *       build (TypeScript, Vite) would never be installed
 *   <li>a non root user wherever the base image provides one
 * </ul>
 */
@Component
public class DockerfileGenerator {

    private static final String NODE_IMAGE = "node:20-alpine";
    private static final String NGINX_IMAGE = "nginx:1.27-alpine";
    private static final String MAVEN_IMAGE = "maven:3.9-eclipse-temurin-21";
    private static final String GRADLE_IMAGE = "gradle:8.10-jdk21";
    private static final String JRE_IMAGE = "eclipse-temurin:21-jre-alpine";
    private static final String PYTHON_IMAGE = "python:3.12-slim";

    /** The generated Dockerfile plus any auxiliary files that must exist in the build context. */
    public record GeneratedBuildFiles(
            String dockerfileName, String dockerfile, Map<String, String> auxiliaryFiles) {}

    public GeneratedBuildFiles generate(Spec spec) {
        return switch (spec.framework().runtime()) {
            case NODE -> spec.framework().isStaticOutput() ? nodeStatic(spec) : nodeServer(spec);
            case STATIC -> plainStatic(spec);
            case JAVA -> java(spec);
            case PYTHON -> python(spec);
            case DOCKER, UNKNOWN ->
                    throw new IllegalStateException(
                            "Dockerfile generation is not applicable to " + spec.framework());
        };
    }

    // ------------------------------------------------------------------ Node

    private GeneratedBuildFiles nodeServer(Spec spec) {
        StringBuilder dockerfile = new StringBuilder();
        header(dockerfile, spec);
        dockerfile.append("FROM ").append(NODE_IMAGE).append("\n");
        dockerfile.append("WORKDIR /app\n");
        dockerfile.append("ENV CI=true\n");
        appendWorkdirCopy(dockerfile, spec);
        run(dockerfile, spec.installCommand());
        run(dockerfile, spec.buildCommand());
        dockerfile.append("ENV NODE_ENV=production\n");
        dockerfile.append("ENV PORT=").append(spec.port()).append("\n");
        dockerfile.append("ENV HOST=0.0.0.0\n");
        dockerfile.append("EXPOSE ").append(spec.port()).append("\n");
        // node:alpine ships an unprivileged "node" user; use it and make the app tree readable by it.
        dockerfile.append("RUN chown -R node:node /app\n");
        dockerfile.append("USER node\n");
        cmd(dockerfile, spec.startCommand() == null ? "npm start" : spec.startCommand());
        return new GeneratedBuildFiles("Dockerfile.deployforge", dockerfile.toString(), Map.of());
    }

    private GeneratedBuildFiles nodeStatic(Spec spec) {
        String outputDirectory =
                StringUtils.hasText(spec.staticOutputDirectory())
                        ? spec.staticOutputDirectory()
                        : spec.framework().staticOutputDirectory();

        StringBuilder dockerfile = new StringBuilder();
        header(dockerfile, spec);
        dockerfile.append("FROM ").append(NODE_IMAGE).append(" AS builder\n");
        dockerfile.append("WORKDIR /app\n");
        dockerfile.append("ENV CI=true\n");
        appendWorkdirCopy(dockerfile, spec);
        run(dockerfile, spec.installCommand());
        run(dockerfile, spec.buildCommand() == null ? "npm run build" : spec.buildCommand());
        dockerfile.append("\n");
        dockerfile.append("FROM ").append(NGINX_IMAGE).append("\n");
        dockerfile
                .append("COPY --from=builder /app/")
                .append(outputDirectory)
                .append(" /usr/share/nginx/html\n");
        dockerfile.append("COPY deployforge-nginx.conf /etc/nginx/conf.d/default.conf\n");
        dockerfile.append("EXPOSE 80\n");
        dockerfile.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");

        Map<String, String> auxiliary = new LinkedHashMap<>();
        auxiliary.put("deployforge-nginx.conf", nginxConf());
        return new GeneratedBuildFiles("Dockerfile.deployforge", dockerfile.toString(), auxiliary);
    }

    private GeneratedBuildFiles plainStatic(Spec spec) {
        StringBuilder dockerfile = new StringBuilder();
        header(dockerfile, spec);
        dockerfile.append("FROM ").append(NGINX_IMAGE).append("\n");
        dockerfile
                .append("COPY ")
                .append(spec.contextSubdirectory())
                .append(" /usr/share/nginx/html\n");
        dockerfile.append("COPY deployforge-nginx.conf /etc/nginx/conf.d/default.conf\n");
        dockerfile.append("EXPOSE 80\n");
        dockerfile.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");

        Map<String, String> auxiliary = new LinkedHashMap<>();
        auxiliary.put("deployforge-nginx.conf", nginxConf());
        return new GeneratedBuildFiles("Dockerfile.deployforge", dockerfile.toString(), auxiliary);
    }

    /** SPA friendly config: unknown paths fall back to index.html instead of returning 404. */
    private String nginxConf() {
        return """
               server {
                   listen 80;
                   server_name _;
                   root /usr/share/nginx/html;
                   index index.html;

                   gzip on;
                   gzip_types text/plain text/css application/json application/javascript
                              application/x-javascript text/xml application/xml image/svg+xml;

                   location /assets/ {
                       expires 1y;
                       add_header Cache-Control "public, immutable";
                       try_files $uri =404;
                   }

                   location / {
                       try_files $uri $uri/ /index.html;
                   }
               }
               """;
    }

    // ------------------------------------------------------------------ JVM

    private GeneratedBuildFiles java(Spec spec) {
        boolean gradle = spec.framework() == Framework.SPRING_BOOT_GRADLE;
        String builderImage = gradle ? GRADLE_IMAGE : MAVEN_IMAGE;
        String artifactDirectory = gradle ? "build/libs" : "target";
        String defaultBuild =
                gradle ? "gradle build -x test --no-daemon" : "mvn -B -DskipTests package";

        StringBuilder dockerfile = new StringBuilder();
        header(dockerfile, spec);
        dockerfile.append("FROM ").append(builderImage).append(" AS builder\n");
        dockerfile.append("WORKDIR /app\n");
        appendWorkdirCopy(dockerfile, spec);
        run(dockerfile, spec.buildCommand() == null ? defaultBuild : spec.buildCommand());
        dockerfile.append("\n");
        dockerfile.append("FROM ").append(JRE_IMAGE).append("\n");
        dockerfile.append("WORKDIR /app\n");
        dockerfile
                .append("COPY --from=builder /app/")
                .append(artifactDirectory)
                .append(" /app/artifacts\n");
        dockerfile.append("ENV SERVER_PORT=").append(spec.port()).append("\n");
        dockerfile.append("ENV PORT=").append(spec.port()).append("\n");
        dockerfile.append("EXPOSE ").append(spec.port()).append("\n");
        dockerfile.append("RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app\n");
        dockerfile.append("USER app\n");
        if (StringUtils.hasText(spec.startCommand())) {
            cmd(dockerfile, spec.startCommand());
        } else {
            // Pick the single runnable jar at start time: '*.jar' in COPY breaks when a build
            // produces both the boot jar and the plain/original artifact.
            dockerfile.append(
                    "CMD [\"sh\", \"-c\", \"exec java -XX:MaxRAMPercentage=75.0 -jar "
                            + "$(ls /app/artifacts/*.jar | grep -v -E 'original|plain|sources|javadoc' | head -n 1)\"]\n");
        }
        return new GeneratedBuildFiles("Dockerfile.deployforge", dockerfile.toString(), Map.of());
    }

    // ------------------------------------------------------------------ Python

    private GeneratedBuildFiles python(Spec spec) {
        StringBuilder dockerfile = new StringBuilder();
        header(dockerfile, spec);
        dockerfile.append("FROM ").append(PYTHON_IMAGE).append("\n");
        dockerfile.append("WORKDIR /app\n");
        dockerfile.append("ENV PYTHONUNBUFFERED=1\n");
        dockerfile.append("ENV PYTHONDONTWRITEBYTECODE=1\n");
        appendWorkdirCopy(dockerfile, spec);
        run(dockerfile, spec.installCommand());
        run(dockerfile, spec.buildCommand());
        dockerfile.append("ENV PORT=").append(spec.port()).append("\n");
        dockerfile.append("EXPOSE ").append(spec.port()).append("\n");
        dockerfile.append(
                "RUN useradd --create-home --shell /bin/sh app && chown -R app:app /app\n");
        dockerfile.append("USER app\n");
        cmd(dockerfile, spec.startCommand() == null ? "python main.py" : spec.startCommand());
        return new GeneratedBuildFiles("Dockerfile.deployforge", dockerfile.toString(), Map.of());
    }

    // ------------------------------------------------------------------ shared

    private void header(StringBuilder dockerfile, Spec spec) {
        dockerfile.append("# Generated by DeployForge AI - do not edit by hand.\n");
        dockerfile.append("# framework: ").append(spec.framework().displayName()).append("\n");
        dockerfile.append("# runtime:   ").append(spec.framework().runtime().displayName()).append("\n");
        dockerfile.append("# Commit a Dockerfile to your repository to take full control of the build.\n\n");
    }

    /** Copies the application directory (respecting a monorepo root directory) into /app. */
    private void appendWorkdirCopy(StringBuilder dockerfile, Spec spec) {
        dockerfile.append("COPY ").append(spec.contextSubdirectory()).append(" ./\n");
    }

    private void run(StringBuilder dockerfile, String command) {
        if (StringUtils.hasText(command)) {
            dockerfile.append("RUN ").append(command.trim()).append("\n");
        }
    }

    /**
     * Uses shell form so that {@code npm start} style commands behave exactly as they do locally, with
     * {@code exec} to keep the process as PID 1 so SIGTERM reaches it and stops are graceful.
     */
    private void cmd(StringBuilder dockerfile, String command) {
        dockerfile
                .append("CMD [\"sh\", \"-c\", \"exec ")
                .append(command.trim().replace("\"", "\\\""))
                .append("\"]\n");
    }

    /**
     * Build inputs.
     *
     * @param contextSubdirectory path inside the build context to copy, {@code "."} for the whole
     *     repository or {@code "apps/web"} for a monorepo package
     */
    public record Spec(
            Framework framework,
            RuntimeType runtime,
            String contextSubdirectory,
            String installCommand,
            String buildCommand,
            String startCommand,
            String staticOutputDirectory,
            int port) {}

    /** Minimal ignore file, only written when the repository does not provide one. */
    public String defaultDockerignore() {
        return """
               .git
               .github
               node_modules
               **/node_modules
               .venv
               venv
               __pycache__
               .pytest_cache
               .gradle
               .idea
               .vscode
               *.log
               """;
    }
}
