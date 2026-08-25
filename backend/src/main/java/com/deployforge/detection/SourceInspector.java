package com.deployforge.detection;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of a repository's files, so detection works identically before and after cloning.
 *
 * <p>Before import there is no checkout yet and files are fetched through the GitHub API; during a
 * deployment the working copy is on disk. Both are behind this interface, which means the framework
 * shown on the import screen is produced by exactly the same code that later configures the build.
 */
public interface SourceInspector {

    /** File contents as UTF-8, or empty when the path does not exist. */
    Optional<String> readFile(String relativePath);

    /** True when the path exists (file or directory). */
    boolean exists(String relativePath);

    /** Entry names directly under {@code relativeDirectory} (non recursive). */
    List<String> listDirectory(String relativeDirectory);

    /** Human readable source description used in log lines. */
    String describe();
}
