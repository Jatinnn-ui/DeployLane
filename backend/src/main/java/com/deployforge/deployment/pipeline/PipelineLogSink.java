package com.deployforge.deployment.pipeline;

import com.deployforge.log.DeploymentLogService;
import com.deployforge.log.LogLevel;
import com.deployforge.log.LogSource;
import com.deployforge.runtime.LogSink;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Adapts runtime output (git, docker build, container stdout) into deployment logs.
 *
 * <p>Two things happen here that matter in practice:
 *
 * <ul>
 *   <li><b>Batching.</b> A {@code docker build} of a Node app emits thousands of lines. Writing one row
 *       per line would hammer PostgreSQL, so lines are buffered and flushed in small batches - still
 *       fast enough to feel live in the browser.
 *   <li><b>Source classification.</b> Docker's own progress lines are tagged {@code DOCKER} while output
 *       produced by the user's install/build commands is tagged {@code BUILD}, which makes the log
 *       viewer's source filter genuinely useful.
 * </ul>
 */
public class PipelineLogSink implements LogSink {

    private static final int FLUSH_THRESHOLD = 25;
    private static final long FLUSH_INTERVAL_MS = 400;

    /**
     * Shared timer that drains a partially filled buffer.
     *
     * <p>Without it, the last few lines of output would sit in memory until the stream closed - which for a
     * container log follow is minutes later. A size- or age-based flush that only runs when the *next* line
     * arrives is not a time-based flush at all.
     */
    private static final ScheduledExecutorService FLUSH_TIMER =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "df-log-flush");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final DeploymentLogService logService;
    private final UUID deploymentId;
    private final LogSource defaultSource;
    private final BooleanSupplier cancellation;
    private final boolean classifyDockerOutput;

    private final List<DeploymentLogService.Line> buffer = new ArrayList<>();
    private LogSource bufferedSource;
    private long lastFlush = System.currentTimeMillis();
    private ScheduledFuture<?> pendingFlush;

    public PipelineLogSink(
            DeploymentLogService logService,
            UUID deploymentId,
            LogSource defaultSource,
            BooleanSupplier cancellation,
            boolean classifyDockerOutput) {
        this.logService = logService;
        this.deploymentId = deploymentId;
        this.defaultSource = defaultSource;
        this.cancellation = cancellation;
        this.classifyDockerOutput = classifyDockerOutput;
        this.bufferedSource = defaultSource;
    }

    @Override
    public synchronized void accept(LogLine line) {
        if (line == null || line.message() == null || line.message().isBlank()) {
            return;
        }
        LogSource source = classify(line.message());
        if (source != bufferedSource) {
            flush();
            bufferedSource = source;
        }
        buffer.add(new DeploymentLogService.Line(levelFor(line), line.message()));

        if (buffer.size() >= FLUSH_THRESHOLD
                || System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL_MS) {
            flush();
        } else if (pendingFlush == null) {
            // Guarantees these lines reach the log even if no further output ever arrives.
            pendingFlush = FLUSH_TIMER.schedule(this::flush, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean cancelled() {
        return cancellation != null && cancellation.getAsBoolean();
    }

    public synchronized void flush() {
        if (pendingFlush != null) {
            pendingFlush.cancel(false);
            pendingFlush = null;
        }
        if (buffer.isEmpty()) {
            lastFlush = System.currentTimeMillis();
            return;
        }
        List<DeploymentLogService.Line> pending = List.copyOf(buffer);
        buffer.clear();
        lastFlush = System.currentTimeMillis();
        logService.appendBatch(deploymentId, bufferedSource, pending);
    }

    private LogLevel levelFor(LogLine line) {
        if (line.stream() == Stream.STDERR) {
            // Build tools write warnings and even progress to stderr, so stderr alone is not an error.
            return looksLikeError(line.message()) ? LogLevel.ERROR : LogLevel.WARN;
        }
        return looksLikeError(line.message()) ? LogLevel.ERROR : LogLevel.INFO;
    }

    private boolean looksLikeError(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("error")
                || lower.contains("npm err!")
                || lower.contains("error:")
                || lower.contains("failed to compile")
                || lower.contains("build failed")
                || lower.contains("fatal:")
                || lower.contains("traceback (most recent call last)")
                || lower.contains("exception in thread")
                || lower.contains("[error]");
    }

    /** Docker's build protocol lines vs output produced inside a RUN instruction. */
    private LogSource classify(String message) {
        if (!classifyDockerOutput) {
            return defaultSource;
        }
        String trimmed = message.stripLeading();
        boolean dockerProtocol =
                trimmed.startsWith("Step ")
                        || trimmed.startsWith("--->")
                        || trimmed.startsWith("#")
                        || trimmed.startsWith("Removing intermediate container")
                        || trimmed.startsWith("Successfully built")
                        || trimmed.startsWith("Successfully tagged")
                        || trimmed.startsWith("Pulling from")
                        || trimmed.startsWith("Digest:")
                        || trimmed.startsWith("Status:");
        return dockerProtocol ? LogSource.DOCKER : LogSource.BUILD;
    }
}
