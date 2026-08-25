package com.deployforge.common.util;

import com.deployforge.common.error.Exceptions.BadRequestException;
import java.util.regex.Pattern;

/**
 * Input validation for values that flow into shell-free process arguments, Docker labels,
 * filesystem paths and hostnames.
 *
 * <p>Everything here fails closed: unknown or unexpected characters are rejected rather than
 * escaped, because the accepted grammars are all narrow.
 */
public final class Validators {

    /** POSIX-ish environment variable name. */
    public static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

    /** Git branch / ref, conservative subset of git-check-ref-format. */
    public static final Pattern BRANCH =
            Pattern.compile("^(?!/)(?!.*//)(?!.*\\.\\.)[A-Za-z0-9._/\\-]{1,255}$");

    /** GitHub owner or repository name. */
    public static final Pattern GITHUB_NAME = Pattern.compile("^[A-Za-z0-9._\\-]{1,100}$");

    /** 40 char hex sha, or short sha. */
    public static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-fA-F]{7,40}$");

    /** Relative path inside a repository. No absolute paths, no traversal, no backslashes. */
    public static final Pattern RELATIVE_PATH =
            Pattern.compile("^(?!/)(?!.*\\.\\.)[A-Za-z0-9._/\\-]{1,255}$");

    /** DNS hostname. */
    public static final Pattern HOSTNAME =
            Pattern.compile("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z]{2,63}$");

    /**
     * Build / start commands. These are executed inside the container by a shell, so we forbid the
     * characters that would let a command escape into another statement or a subshell. Users who
     * need arbitrary shell logic can commit their own Dockerfile instead.
     */
    private static final Pattern FORBIDDEN_IN_COMMAND = Pattern.compile("[;`$&|<>\\n\\r\\\\]");

    private Validators() {}

    public static String requireEnvKey(String key) {
        if (key == null || !ENV_KEY.matcher(key).matches()) {
            throw new BadRequestException(
                    "Environment variable name must match [A-Za-z_][A-Za-z0-9_]* and be at most 128 characters");
        }
        return key;
    }

    public static String requireBranch(String branch) {
        if (branch == null || !BRANCH.matcher(branch).matches()) {
            throw new BadRequestException("Branch name '" + safe(branch) + "' is not a valid git ref");
        }
        return branch;
    }

    public static String requireGithubName(String value, String field) {
        if (value == null || !GITHUB_NAME.matcher(value).matches()) {
            throw new BadRequestException(field + " is not a valid GitHub identifier");
        }
        return value;
    }

    public static String requireCommitSha(String sha) {
        if (sha == null || !COMMIT_SHA.matcher(sha).matches()) {
            throw new BadRequestException("Commit sha is not valid");
        }
        return sha;
    }

    /** Normalises an optional repository relative path such as {@code apps/web}. */
    public static String normalizeRelativePath(String path, String field) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim().replace('\\', '/');
        while (trimmed.startsWith("./")) {
            trimmed = trimmed.substring(2);
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!RELATIVE_PATH.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    field + " must be a relative path without '..' segments");
        }
        return trimmed;
    }

    public static String requireCommand(String command, String field) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String trimmed = command.trim();
        if (trimmed.length() > 1000) {
            throw new BadRequestException(field + " is too long");
        }
        if (FORBIDDEN_IN_COMMAND.matcher(trimmed).find()) {
            throw new BadRequestException(
                    field
                            + " may not contain shell control characters (; | & ` $ < > \\). Commit a Dockerfile for advanced build logic.");
        }
        return trimmed;
    }

    public static int requirePort(Integer port, String field) {
        if (port == null || port < 1 || port > 65535) {
            throw new BadRequestException(field + " must be between 1 and 65535");
        }
        return port;
    }

    public static String requireHealthPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/") || trimmed.contains("..") || trimmed.length() > 512) {
            throw new BadRequestException("Health check path must be an absolute path such as /healthz");
        }
        return trimmed;
    }

    public static String requireHostname(String hostname) {
        if (hostname == null || !HOSTNAME.matcher(hostname.toLowerCase()).matches()) {
            throw new BadRequestException("'" + safe(hostname) + "' is not a valid hostname");
        }
        return hostname.toLowerCase();
    }

    private static String safe(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9._/\\-]", "");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
