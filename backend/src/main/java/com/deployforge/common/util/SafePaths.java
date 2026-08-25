package com.deployforge.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem helpers that refuse to leave a designated root directory.
 *
 * <p>Deployment working directories are derived from user controlled values (repository names,
 * project slugs, root directories), so every join goes through {@link #resolveInside} which
 * normalises and then verifies containment.
 */
public final class SafePaths {

    private static final Logger log = LoggerFactory.getLogger(SafePaths.class);

    private SafePaths() {}

    /**
     * Resolves {@code relative} against {@code root} and guarantees the result stays inside
     * {@code root}.
     *
     * @throws IllegalArgumentException if the resolved path escapes the root
     */
    public static Path resolveInside(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (relative == null || relative.isBlank()) {
            return normalizedRoot;
        }
        Path candidate = normalizedRoot.resolve(relative.replace('\\', '/')).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path '" + relative + "' escapes its root directory");
        }
        return candidate;
    }

    public static Path createDirectories(Path path) throws IOException {
        return Files.createDirectories(path);
    }

    /** Recursively deletes a directory tree, tolerating a missing directory. Never throws. */
    public static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.debug("cleanup_delete_failed path={} reason={}", p, e.getMessage());
                                }
                            });
        } catch (IOException e) {
            log.warn("cleanup_walk_failed path={} reason={}", path, e.getMessage());
        }
    }

    public static long directorySize(Path path) {
        if (path == null || !Files.exists(path)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(
                            p -> {
                                try {
                                    return Files.size(p);
                                } catch (IOException e) {
                                    return 0L;
                                }
                            })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }
}
