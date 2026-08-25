package com.deployforge.runtime;

/**
 * Receives output produced by a long running runtime operation, line by line.
 *
 * <p>The pipeline wires this to the deployment log service so build output is persisted and pushed
 * over WebSocket while the build is still running - not collected at the end.
 */
public interface LogSink {

    void accept(LogLine line);

    /** Cancellation signal, polled by implementations between chunks. */
    default boolean cancelled() {
        return false;
    }

    enum Stream {
        STDOUT,
        STDERR
    }

    record LogLine(Stream stream, String message) {
        public static LogLine out(String message) {
            return new LogLine(Stream.STDOUT, message);
        }

        public static LogLine err(String message) {
            return new LogLine(Stream.STDERR, message);
        }
    }
}
