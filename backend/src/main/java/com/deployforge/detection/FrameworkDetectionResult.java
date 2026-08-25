package com.deployforge.detection;

import java.util.List;

/**
 * Outcome of framework detection.
 *
 * <p>{@code confidence} is a genuine signal, not decoration: an explicit Dockerfile is 0.99, a
 * dependency match is around 0.9, a guess from file layout is closer to 0.5. The UI shows it, and
 * anything below 0.6 is presented as "please confirm".
 *
 * <p>{@code evidence} lists the concrete files and dependencies that led to the conclusion, which is
 * what makes the result reviewable instead of magic.
 */
public record FrameworkDetectionResult(
        Framework framework,
        RuntimeType runtime,
        String installCommand,
        String buildCommand,
        String startCommand,
        int port,
        String dockerfilePath,
        PackageManager packageManager,
        double confidence,
        List<String> evidence,
        List<String> warnings) {

    public static FrameworkDetectionResult unknown(List<String> evidence) {
        return new FrameworkDetectionResult(
                Framework.UNKNOWN,
                RuntimeType.UNKNOWN,
                null,
                null,
                null,
                Framework.UNKNOWN.defaultPort(),
                null,
                PackageManager.NONE,
                0.0,
                evidence,
                List.of(
                        "DeployForge could not identify this project. Add a Dockerfile, or set the build and start commands manually."));
    }

    public boolean requiresConfirmation() {
        return confidence < 0.6;
    }

    public String frameworkLabel() {
        return framework.displayName();
    }
}
