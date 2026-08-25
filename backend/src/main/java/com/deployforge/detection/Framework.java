package com.deployforge.detection;

/**
 * Frameworks DeployForge can build without a user supplied Dockerfile.
 *
 * <p>Each constant carries the defaults the deployment pipeline uses when the user does not override
 * them. Keeping the defaults on the enum means detection, the import UI and the Dockerfile generator
 * all agree by construction.
 */
public enum Framework {
    NEXTJS("Next.js", RuntimeType.NODE, "npm ci", "npm run build", "npm run start", 3000),
    REACT_VITE("React + Vite", RuntimeType.NODE, "npm ci", "npm run build", null, 80),
    CREATE_REACT_APP("Create React App", RuntimeType.NODE, "npm ci", "npm run build", null, 80),
    VUE("Vue", RuntimeType.NODE, "npm ci", "npm run build", null, 80),
    SVELTE_KIT("SvelteKit", RuntimeType.NODE, "npm ci", "npm run build", "node build", 3000),
    ASTRO("Astro", RuntimeType.NODE, "npm ci", "npm run build", null, 80),
    NESTJS("NestJS", RuntimeType.NODE, "npm ci", "npm run build", "npm run start:prod", 3000),
    EXPRESS("Express", RuntimeType.NODE, "npm ci", null, "npm start", 3000),
    NODE("Node.js", RuntimeType.NODE, "npm ci", null, "npm start", 3000),

    SPRING_BOOT_MAVEN("Spring Boot (Maven)", RuntimeType.JAVA, null, "mvn -B -DskipTests package", null, 8080),
    SPRING_BOOT_GRADLE("Spring Boot (Gradle)", RuntimeType.JAVA, null, "gradle build -x test", null, 8080),

    FASTAPI("FastAPI", RuntimeType.PYTHON, "pip install --no-cache-dir -r requirements.txt", null,
            "uvicorn main:app --host 0.0.0.0 --port 8000", 8000),
    FLASK("Flask", RuntimeType.PYTHON, "pip install --no-cache-dir -r requirements.txt", null,
            "gunicorn --bind 0.0.0.0:8000 app:app", 8000),
    DJANGO("Django", RuntimeType.PYTHON, "pip install --no-cache-dir -r requirements.txt", null,
            "gunicorn --bind 0.0.0.0:8000 config.wsgi:application", 8000),
    PYTHON("Python", RuntimeType.PYTHON, "pip install --no-cache-dir -r requirements.txt", null,
            "python main.py", 8000),

    STATIC("Static site", RuntimeType.STATIC, null, null, null, 80),

    /** The repository ships its own Dockerfile - DeployForge builds it verbatim. */
    DOCKER("Dockerfile", RuntimeType.DOCKER, null, null, null, 8080),

    UNKNOWN("Unknown", RuntimeType.UNKNOWN, null, null, null, 8080);

    private final String displayName;
    private final RuntimeType runtime;
    private final String defaultInstallCommand;
    private final String defaultBuildCommand;
    private final String defaultStartCommand;
    private final int defaultPort;

    Framework(
            String displayName,
            RuntimeType runtime,
            String defaultInstallCommand,
            String defaultBuildCommand,
            String defaultStartCommand,
            int defaultPort) {
        this.displayName = displayName;
        this.runtime = runtime;
        this.defaultInstallCommand = defaultInstallCommand;
        this.defaultBuildCommand = defaultBuildCommand;
        this.defaultStartCommand = defaultStartCommand;
        this.defaultPort = defaultPort;
    }

    public String displayName() {
        return displayName;
    }

    public RuntimeType runtime() {
        return runtime;
    }

    public String defaultInstallCommand() {
        return defaultInstallCommand;
    }

    public String defaultBuildCommand() {
        return defaultBuildCommand;
    }

    public String defaultStartCommand() {
        return defaultStartCommand;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /**
     * Frameworks whose output is a directory of files rather than a long running process. These are
     * served by a static web server in the final image, so they have no start command.
     */
    public boolean isStaticOutput() {
        return runtime == RuntimeType.STATIC
                || this == REACT_VITE
                || this == CREATE_REACT_APP
                || this == VUE
                || this == ASTRO;
    }

    /** Where the build output lands, relative to the app directory. */
    public String staticOutputDirectory() {
        return switch (this) {
            case CREATE_REACT_APP -> "build";
            case ASTRO -> "dist";
            case VUE, REACT_VITE -> "dist";
            case STATIC -> ".";
            default -> "dist";
        };
    }
}
