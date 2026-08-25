package com.deployforge.deployment.pipeline;

/**
 * An expected, explainable pipeline failure.
 *
 * <p>{@code remediation} is a short, human hint shown alongside the error in the UI. Deployment
 * failures are the moment a platform is judged; "Something went wrong" is not acceptable output.
 */
public class StepFailedException extends RuntimeException {

    private final String remediation;
    private final Integer exitCode;

    public StepFailedException(String message) {
        this(message, null, null, null);
    }

    public StepFailedException(String message, String remediation) {
        this(message, remediation, null, null);
    }

    public StepFailedException(String message, String remediation, Integer exitCode) {
        this(message, remediation, exitCode, null);
    }

    public StepFailedException(String message, String remediation, Integer exitCode, Throwable cause) {
        super(message, cause);
        this.remediation = remediation;
        this.exitCode = exitCode;
    }

    public String getRemediation() {
        return remediation;
    }

    public Integer getExitCode() {
        return exitCode;
    }
}
