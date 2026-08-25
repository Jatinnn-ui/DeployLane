package com.deployforge.runtime;

/**
 * Typed runtime failures.
 *
 * <p>The pipeline distinguishes these deliberately: a build that exits non-zero is the user's
 * problem and should be analysed by AI, a timeout needs a different message, and an unreachable
 * engine is a platform incident that must not be reported as "your build failed".
 */
public final class RuntimeExceptions {

    private RuntimeExceptions() {}

    /** Base type so callers can catch all runtime problems at one boundary. */
    public abstract static class RuntimeOperationException extends RuntimeException {
        protected RuntimeOperationException(String message) {
            super(message);
        }

        protected RuntimeOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The container engine itself is unreachable or misconfigured. Platform side problem. */
    public static class RuntimeUnavailableException extends RuntimeOperationException {
        public RuntimeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public RuntimeUnavailableException(String message) {
            super(message);
        }
    }

    /** The image build failed. {@code exitCode} is the builder's exit status when known. */
    public static class BuildFailedException extends RuntimeOperationException {
        private final Integer exitCode;

        public BuildFailedException(String message, Integer exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        public Integer getExitCode() {
            return exitCode;
        }
    }

    /** A build or container operation exceeded its time budget. */
    public static class OperationTimedOutException extends RuntimeOperationException {
        public OperationTimedOutException(String message) {
            super(message);
        }
    }

    /** The image built, but the container could not be created or started. */
    public static class ContainerStartException extends RuntimeOperationException {
        public ContainerStartException(String message) {
            super(message);
        }

        public ContainerStartException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The operation was cancelled by the user. */
    public static class OperationCancelledException extends RuntimeOperationException {
        public OperationCancelledException(String message) {
            super(message);
        }
    }
}
