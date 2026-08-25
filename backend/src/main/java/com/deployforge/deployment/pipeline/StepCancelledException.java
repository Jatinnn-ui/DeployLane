package com.deployforge.deployment.pipeline;

/** Raised when a user cancelled the deployment while this step was running. */
public class StepCancelledException extends RuntimeException {

    public StepCancelledException(String message) {
        super(message);
    }
}
