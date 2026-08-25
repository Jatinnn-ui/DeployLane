package com.deployforge.detection;

/**
 * Node package manager, inferred from the lockfile.
 *
 * <p>This matters for real builds: {@code npm ci} fails outright without a {@code package-lock.json},
 * and a repository locked with pnpm installed by npm produces a different dependency tree than the
 * developer tested.
 */
public enum PackageManager {
    NPM("package-lock.json", "npm ci", "npm install"),
    YARN("yarn.lock", "yarn install --frozen-lockfile", "yarn install"),
    PNPM("pnpm-lock.yaml", "pnpm install --frozen-lockfile", "pnpm install"),
    BUN("bun.lockb", "bun install --frozen-lockfile", "bun install"),
    NONE(null, null, "npm install");

    private final String lockfile;
    private final String frozenInstallCommand;
    private final String looseInstallCommand;

    PackageManager(String lockfile, String frozenInstallCommand, String looseInstallCommand) {
        this.lockfile = lockfile;
        this.frozenInstallCommand = frozenInstallCommand;
        this.looseInstallCommand = looseInstallCommand;
    }

    public String lockfile() {
        return lockfile;
    }

    /** Reproducible install when a lockfile is present, otherwise the tolerant variant. */
    public String installCommand(boolean lockfilePresent) {
        return lockfilePresent && frozenInstallCommand != null
                ? frozenInstallCommand
                : looseInstallCommand;
    }

    public String runCommand(String script) {
        return switch (this) {
            case YARN -> "yarn " + script;
            case PNPM -> "pnpm run " + script;
            case BUN -> "bun run " + script;
            default -> "npm run " + script;
        };
    }
}
