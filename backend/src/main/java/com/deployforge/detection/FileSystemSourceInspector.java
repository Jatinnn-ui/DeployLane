package com.deployforge.detection;

import com.deployforge.common.util.SafePaths;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects a checked out working copy.
 *
 * <p>Every path goes through {@link SafePaths#resolveInside} so a crafted root directory cannot read
 * outside the clone.
 */
public class FileSystemSourceInspector implements SourceInspector {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSourceInspector.class);
    private static final long MAX_READ_BYTES = 512 * 1024;

    private final Path root;

    public FileSystemSourceInspector(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<String> readFile(String relativePath) {
        try {
            Path file = SafePaths.resolveInside(root, relativePath);
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_READ_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (MalformedInputException e) {
            // Binary file where we expected text - treat as absent rather than failing detection.
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            log.debug("inspect_read_failed path={} reason={}", relativePath, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return Files.exists(SafePaths.resolveInside(root, relativePath));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public List<String> listDirectory(String relativeDirectory) {
        try {
            Path directory = SafePaths.resolveInside(root, relativeDirectory);
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            try (Stream<Path> entries = Files.list(directory)) {
                return entries.map(path -> path.getFileName().toString()).sorted().toList();
            }
        } catch (IOException | IllegalArgumentException e) {
            return List.of();
        }
    }

    @Override
    public String describe() {
        return "working copy " + root.getFileName();
    }
}
