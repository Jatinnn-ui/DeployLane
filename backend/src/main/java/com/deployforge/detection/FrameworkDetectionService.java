package com.deployforge.detection;

import com.deployforge.common.util.JsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Identifies what a repository is and how it should be built.
 *
 * <p>Detection order is deliberate, from strongest to weakest signal:
 *
 * <ol>
 *   <li>a committed {@code Dockerfile} - the user has already answered the question
 *   <li>{@code package.json} dependencies, plus the declared scripts and the lockfile
 *   <li>{@code pom.xml} / {@code build.gradle}
 *   <li>{@code requirements.txt} / {@code pyproject.toml}
 *   <li>a bare {@code index.html}, which means a static site
 * </ol>
 *
 * <p>The result is advisory: everything it returns can be overridden by the user, and the returned
 * evidence explains why it decided what it decided.
 */
@Service
public class FrameworkDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FrameworkDetectionService.class);

    private final JsonCodec json;

    public FrameworkDetectionService(JsonCodec json) {
        this.json = json;
    }

    /**
     * @param inspector source of files, either a GitHub repository or a checked out clone
     * @param rootDirectory optional sub directory for monorepos, already validated
     */
    public FrameworkDetectionResult detect(SourceInspector inspector, String rootDirectory) {
        String prefix = rootDirectory == null || rootDirectory.isBlank() ? "" : rootDirectory + "/";
        List<String> evidence = new ArrayList<>();

        Optional<FrameworkDetectionResult> dockerfile = detectDockerfile(inspector, prefix, evidence);
        if (dockerfile.isPresent()) {
            return dockerfile.get();
        }

        Optional<String> packageJson = inspector.readFile(prefix + "package.json");
        if (packageJson.isPresent()) {
            return detectNode(inspector, prefix, packageJson.get(), evidence);
        }

        if (inspector.readFile(prefix + "pom.xml").isPresent()) {
            return detectMaven(inspector.readFile(prefix + "pom.xml").orElse(""), evidence);
        }
        if (inspector.exists(prefix + "build.gradle") || inspector.exists(prefix + "build.gradle.kts")) {
            return detectGradle(inspector, prefix, evidence);
        }

        Optional<FrameworkDetectionResult> python = detectPython(inspector, prefix, evidence);
        if (python.isPresent()) {
            return python.get();
        }

        if (inspector.exists(prefix + "index.html")) {
            evidence.add("index.html at the project root, and no build tooling");
            return new FrameworkDetectionResult(
                    Framework.STATIC,
                    RuntimeType.STATIC,
                    null,
                    null,
                    null,
                    Framework.STATIC.defaultPort(),
                    null,
                    PackageManager.NONE,
                    0.7,
                    evidence,
                    List.of());
        }

        log.info("framework_detection_unknown source={}", inspector.describe());
        evidence.add("No package.json, pom.xml, build.gradle, requirements.txt or Dockerfile found");
        return FrameworkDetectionResult.unknown(evidence);
    }

    // ------------------------------------------------------------------ Dockerfile

    private Optional<FrameworkDetectionResult> detectDockerfile(
            SourceInspector inspector, String prefix, List<String> evidence) {
        for (String candidate : List.of("Dockerfile", "dockerfile", "docker/Dockerfile")) {
            String path = prefix + candidate;
            Optional<String> content = inspector.readFile(path);
            if (content.isEmpty()) {
                continue;
            }
            evidence.add(candidate + " is committed, so DeployForge builds it as-is");
            int port = parseExposedPort(content.get()).orElse(Framework.DOCKER.defaultPort());
            if (parseExposedPort(content.get()).isPresent()) {
                evidence.add("EXPOSE " + port + " found in " + candidate);
            }
            return Optional.of(
                    new FrameworkDetectionResult(
                            Framework.DOCKER,
                            RuntimeType.DOCKER,
                            null,
                            null,
                            null,
                            port,
                            path,
                            PackageManager.NONE,
                            0.99,
                            evidence,
                            parseExposedPort(content.get()).isPresent()
                                    ? List.of()
                                    : List.of(
                                            "No EXPOSE directive found. DeployForge assumes port "
                                                    + port
                                                    + "; change it in project settings if that is wrong.")));
        }
        return Optional.empty();
    }

    private Optional<Integer> parseExposedPort(String dockerfile) {
        return dockerfile
                .lines()
                .map(String::trim)
                .filter(line -> line.toUpperCase(Locale.ROOT).startsWith("EXPOSE "))
                .map(line -> line.substring(7).trim().split("[\\s/]")[0])
                .map(
                        value -> {
                            try {
                                return Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                .filter(port -> port != null && port > 0 && port < 65536)
                .findFirst();
    }

    // ------------------------------------------------------------------ Node

    private FrameworkDetectionResult detectNode(
            SourceInspector inspector, String prefix, String packageJsonContent, List<String> evidence) {
        evidence.add("package.json found");
        JsonNode packageJson = parseJson(packageJsonContent);
        JsonNode dependencies = merged(packageJson);
        JsonNode scripts = packageJson == null ? null : packageJson.get("scripts");

        PackageManager packageManager = detectPackageManager(inspector, prefix, evidence);
        boolean lockfilePresent =
                packageManager.lockfile() != null && inspector.exists(prefix + packageManager.lockfile());

        Framework framework;
        double confidence;
        if (has(dependencies, "next")) {
            framework = Framework.NEXTJS;
            confidence = 0.95;
            evidence.add("dependency 'next'");
        } else if (has(dependencies, "@nestjs/core")) {
            framework = Framework.NESTJS;
            confidence = 0.95;
            evidence.add("dependency '@nestjs/core'");
        } else if (has(dependencies, "@sveltejs/kit")) {
            framework = Framework.SVELTE_KIT;
            confidence = 0.93;
            evidence.add("dependency '@sveltejs/kit'");
        } else if (has(dependencies, "astro")) {
            framework = Framework.ASTRO;
            confidence = 0.92;
            evidence.add("dependency 'astro'");
        } else if (has(dependencies, "vite") && has(dependencies, "react")) {
            framework = Framework.REACT_VITE;
            confidence = 0.94;
            evidence.add("dependencies 'vite' + 'react'");
        } else if (has(dependencies, "vite") && has(dependencies, "vue")) {
            framework = Framework.VUE;
            confidence = 0.92;
            evidence.add("dependencies 'vite' + 'vue'");
        } else if (has(dependencies, "react-scripts")) {
            framework = Framework.CREATE_REACT_APP;
            confidence = 0.9;
            evidence.add("dependency 'react-scripts'");
        } else if (has(dependencies, "vue")) {
            framework = Framework.VUE;
            confidence = 0.8;
            evidence.add("dependency 'vue'");
        } else if (has(dependencies, "express")
                || has(dependencies, "fastify")
                || has(dependencies, "koa")) {
            framework = Framework.EXPRESS;
            confidence = 0.85;
            evidence.add("an HTTP server dependency (express / fastify / koa)");
        } else {
            framework = Framework.NODE;
            confidence = 0.65;
            evidence.add("no known framework dependency, treating it as a plain Node.js service");
        }

        String buildScript = scriptOf(scripts, "build");
        String startScript = scriptOf(scripts, "start");
        List<String> warnings = new ArrayList<>();

        String buildCommand = null;
        if (buildScript != null) {
            buildCommand = packageManager.runCommand("build");
            evidence.add("script 'build' -> " + truncate(buildScript));
        } else if (framework.defaultBuildCommand() != null && !framework.isStaticOutput()) {
            warnings.add(
                    "package.json has no 'build' script; DeployForge will skip the build step for this project.");
        } else if (framework.isStaticOutput()) {
            warnings.add(
                    "package.json has no 'build' script, but "
                            + framework.displayName()
                            + " produces static output. Add a build script or the image will be empty.");
        }

        String startCommand = null;
        if (!framework.isStaticOutput()) {
            if (startScript != null) {
                startCommand = packageManager.runCommand("start").replace("run start", "start");
                if (packageManager == PackageManager.NPM) {
                    startCommand = "npm start";
                }
                evidence.add("script 'start' -> " + truncate(startScript));
            } else {
                startCommand = framework.defaultStartCommand();
                if (startCommand == null) {
                    String main = textOf(packageJson, "main");
                    startCommand = main == null ? "node index.js" : "node " + main;
                }
                warnings.add(
                        "package.json has no 'start' script; DeployForge will run '" + startCommand + "'.");
            }
        }

        int port = framework.defaultPort();

        return new FrameworkDetectionResult(
                framework,
                RuntimeType.NODE,
                packageManager.installCommand(lockfilePresent),
                buildCommand,
                startCommand,
                port,
                null,
                packageManager,
                confidence,
                evidence,
                warnings);
    }

    private PackageManager detectPackageManager(
            SourceInspector inspector, String prefix, List<String> evidence) {
        for (PackageManager candidate :
                List.of(PackageManager.PNPM, PackageManager.YARN, PackageManager.BUN, PackageManager.NPM)) {
            if (candidate.lockfile() != null && inspector.exists(prefix + candidate.lockfile())) {
                evidence.add(candidate.lockfile() + " -> " + candidate.name().toLowerCase(Locale.ROOT));
                return candidate;
            }
        }
        evidence.add("no lockfile found, defaulting to npm install");
        return PackageManager.NPM;
    }

    // ------------------------------------------------------------------ Java

    private FrameworkDetectionResult detectMaven(String pomXml, List<String> evidence) {
        evidence.add("pom.xml found");
        boolean springBoot =
                pomXml.contains("spring-boot-starter-parent") || pomXml.contains("spring-boot-starter");
        if (springBoot) {
            evidence.add("Spring Boot starter declared in pom.xml");
        }
        return new FrameworkDetectionResult(
                Framework.SPRING_BOOT_MAVEN,
                RuntimeType.JAVA,
                null,
                Framework.SPRING_BOOT_MAVEN.defaultBuildCommand(),
                null,
                Framework.SPRING_BOOT_MAVEN.defaultPort(),
                null,
                PackageManager.NONE,
                springBoot ? 0.93 : 0.6,
                evidence,
                springBoot
                        ? List.of()
                        : List.of(
                                "This is a Maven project but Spring Boot was not detected. DeployForge will run the packaged jar; confirm that the build produces an executable jar."));
    }

    private FrameworkDetectionResult detectGradle(
            SourceInspector inspector, String prefix, List<String> evidence) {
        String buildFile =
                inspector.exists(prefix + "build.gradle.kts") ? "build.gradle.kts" : "build.gradle";
        evidence.add(buildFile + " found");
        String content = inspector.readFile(prefix + buildFile).orElse("");
        boolean springBoot = content.contains("org.springframework.boot");
        if (springBoot) {
            evidence.add("Spring Boot plugin declared in " + buildFile);
        }
        boolean wrapper = inspector.exists(prefix + "gradlew");
        return new FrameworkDetectionResult(
                Framework.SPRING_BOOT_GRADLE,
                RuntimeType.JAVA,
                null,
                wrapper ? "./gradlew build -x test" : Framework.SPRING_BOOT_GRADLE.defaultBuildCommand(),
                null,
                Framework.SPRING_BOOT_GRADLE.defaultPort(),
                null,
                PackageManager.NONE,
                springBoot ? 0.9 : 0.55,
                evidence,
                springBoot ? List.of() : List.of("Gradle project without the Spring Boot plugin - verify the build command."));
    }

    // ------------------------------------------------------------------ Python

    private Optional<FrameworkDetectionResult> detectPython(
            SourceInspector inspector, String prefix, List<String> evidence) {
        Optional<String> requirements = inspector.readFile(prefix + "requirements.txt");
        Optional<String> pyproject = inspector.readFile(prefix + "pyproject.toml");
        if (requirements.isEmpty() && pyproject.isEmpty()) {
            return Optional.empty();
        }
        String manifest =
                (requirements.orElse("") + "\n" + pyproject.orElse("")).toLowerCase(Locale.ROOT);
        evidence.add(requirements.isPresent() ? "requirements.txt found" : "pyproject.toml found");

        Framework framework;
        double confidence;
        List<String> warnings = new ArrayList<>();
        if (manifest.contains("fastapi")) {
            framework = Framework.FASTAPI;
            confidence = 0.92;
            evidence.add("dependency 'fastapi'");
        } else if (manifest.contains("django")) {
            framework = Framework.DJANGO;
            confidence = 0.9;
            evidence.add("dependency 'django'");
        } else if (manifest.contains("flask")) {
            framework = Framework.FLASK;
            confidence = 0.9;
            evidence.add("dependency 'flask'");
        } else {
            framework = Framework.PYTHON;
            confidence = 0.6;
            evidence.add("no known web framework dependency, treating it as a plain Python service");
        }

        String startCommand = framework.defaultStartCommand();
        if (framework == Framework.FASTAPI) {
            String module = inspector.exists(prefix + "app/main.py") ? "app.main:app" : "main:app";
            startCommand = "uvicorn " + module + " --host 0.0.0.0 --port " + framework.defaultPort();
            evidence.add("ASGI entrypoint assumed at " + module);
        }
        if (framework == Framework.PYTHON && !inspector.exists(prefix + "main.py")) {
            warnings.add("No main.py found - set the start command manually in project settings.");
        }
        if (requirements.isEmpty()) {
            warnings.add(
                    "Only pyproject.toml is present. DeployForge installs with pip and requires a requirements.txt, or supply your own Dockerfile.");
        }

        return Optional.of(
                new FrameworkDetectionResult(
                        framework,
                        RuntimeType.PYTHON,
                        framework.defaultInstallCommand(),
                        null,
                        startCommand,
                        framework.defaultPort(),
                        null,
                        PackageManager.NONE,
                        confidence,
                        evidence,
                        warnings));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode parseJson(String content) {
        try {
            return json.mapper().readTree(content);
        } catch (Exception e) {
            log.debug("package_json_unparseable reason={}", e.getMessage());
            return null;
        }
    }

    /** dependencies + devDependencies as one lookup surface. */
    private JsonNode merged(JsonNode packageJson) {
        if (packageJson == null) {
            return null;
        }
        var merged = json.mapper().createObjectNode();
        for (String field : List.of("dependencies", "devDependencies", "peerDependencies")) {
            JsonNode node = packageJson.get(field);
            if (node != null && node.isObject()) {
                node.fieldNames().forEachRemaining(name -> merged.put(name, node.get(name).asText("")));
            }
        }
        return merged;
    }

    private boolean has(JsonNode dependencies, String name) {
        return dependencies != null && dependencies.has(name);
    }

    private String scriptOf(JsonNode scripts, String name) {
        if (scripts == null || !scripts.hasNonNull(name)) {
            return null;
        }
        String value = scripts.get(name).asText("");
        return value.isBlank() ? null : value;
    }

    private String textOf(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText("");
        return value.isBlank() ? null : value;
    }

    private String truncate(String value) {
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }
}
