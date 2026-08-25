package com.deployforge.runtime.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.detection.Framework;
import com.deployforge.detection.RuntimeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Generated Dockerfiles are executed verbatim by the engine, so the properties that make them correct are
 * asserted here rather than eyeballed once.
 */
class DockerfileGeneratorTest {

    private final DockerfileGenerator generator = new DockerfileGenerator();

    @Test
    @DisplayName("a Node server image installs, builds, then switches to production and a non root user")
    void generatesNodeServerDockerfile() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.NEXTJS,
                                RuntimeType.NODE,
                                ".",
                                "npm ci",
                                "npm run build",
                                "npm start",
                                null,
                                3000));

        String dockerfile = files.dockerfile();
        assertThat(dockerfile).contains("FROM node:20-alpine");
        assertThat(dockerfile).contains("RUN npm ci");
        assertThat(dockerfile).contains("RUN npm run build");
        assertThat(dockerfile).contains("EXPOSE 3000");
        assertThat(dockerfile).contains("ENV PORT=3000");
        assertThat(dockerfile).contains("USER node");
        assertThat(dockerfile).contains("exec npm start");

        // NODE_ENV must come after the install, otherwise devDependencies needed to build are skipped.
        assertThat(dockerfile.indexOf("ENV NODE_ENV=production")).isGreaterThan(dockerfile.indexOf("RUN npm ci"));
    }

    @Test
    @DisplayName("a static site is built with Node and served by nginx with an SPA fallback")
    void generatesStaticDockerfile() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.REACT_VITE,
                                RuntimeType.NODE,
                                ".",
                                "npm ci",
                                "npm run build",
                                null,
                                "dist",
                                80));

        assertThat(files.dockerfile()).contains("AS builder");
        assertThat(files.dockerfile()).contains("FROM nginx:1.27-alpine");
        assertThat(files.dockerfile()).contains("COPY --from=builder /app/dist /usr/share/nginx/html");
        assertThat(files.dockerfile()).contains("EXPOSE 80");
        assertThat(files.auxiliaryFiles()).containsKey("deployforge-nginx.conf");
        assertThat(files.auxiliaryFiles().get("deployforge-nginx.conf"))
                .contains("try_files $uri $uri/ /index.html");
    }

    @Test
    @DisplayName("a Spring Boot image builds with Maven and runs on a JRE, picking the boot jar at start")
    void generatesJavaDockerfile() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.SPRING_BOOT_MAVEN,
                                RuntimeType.JAVA,
                                ".",
                                null,
                                "mvn -B -DskipTests package",
                                null,
                                null,
                                8080));

        String dockerfile = files.dockerfile();
        assertThat(dockerfile).contains("FROM maven:3.9-eclipse-temurin-21 AS builder");
        assertThat(dockerfile).contains("FROM eclipse-temurin:21-jre-alpine");
        assertThat(dockerfile).contains("COPY --from=builder /app/target /app/artifacts");
        assertThat(dockerfile).contains("MaxRAMPercentage");
        // The plain/original artefacts must be filtered out or the container starts the wrong jar.
        assertThat(dockerfile).contains("grep -v -E 'original|plain|sources|javadoc'");
        assertThat(dockerfile).contains("USER app");
    }

    @Test
    @DisplayName("a Python image installs requirements, disables buffering and drops privileges")
    void generatesPythonDockerfile() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.FASTAPI,
                                RuntimeType.PYTHON,
                                ".",
                                "pip install --no-cache-dir -r requirements.txt",
                                null,
                                "uvicorn main:app --host 0.0.0.0 --port 8000",
                                null,
                                8000));

        String dockerfile = files.dockerfile();
        assertThat(dockerfile).contains("FROM python:3.12-slim");
        assertThat(dockerfile).contains("ENV PYTHONUNBUFFERED=1");
        assertThat(dockerfile).contains("RUN pip install --no-cache-dir -r requirements.txt");
        assertThat(dockerfile).contains("exec uvicorn main:app --host 0.0.0.0 --port 8000");
        assertThat(dockerfile).contains("USER app");
    }

    @Test
    @DisplayName("a monorepo root directory is the copied build context")
    void respectsRootDirectory() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.NEXTJS,
                                RuntimeType.NODE,
                                "apps/web",
                                "npm ci",
                                "npm run build",
                                "npm start",
                                null,
                                3000));

        assertThat(files.dockerfile()).contains("COPY apps/web ./");
    }

    @Test
    @DisplayName("the default ignore file excludes the expensive directories")
    void defaultDockerignore() {
        String ignore = generator.defaultDockerignore();

        assertThat(ignore).contains(".git");
        assertThat(ignore).contains("node_modules");
        assertThat(ignore).contains("__pycache__");
    }

    @Test
    @DisplayName("a generated file is never named Dockerfile, so a committed one is never overwritten")
    void neverOverwritesCommittedDockerfile() {
        DockerfileGenerator.GeneratedBuildFiles files =
                generator.generate(
                        new DockerfileGenerator.Spec(
                                Framework.NODE, RuntimeType.NODE, ".", "npm install", null, "npm start", null, 3000));

        assertThat(files.dockerfileName()).isEqualTo("Dockerfile.deployforge");
    }
}
