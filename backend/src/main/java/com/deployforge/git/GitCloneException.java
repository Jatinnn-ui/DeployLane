package com.deployforge.git;

/**
 * A clone could not be completed.
 *
 * <p>{@code authenticationFailure} is tracked separately because it needs a completely different
 * message: the user must reconnect GitHub or grant the {@code repo} scope, not debug their code.
 */
public class GitCloneException extends RuntimeException {

    private final boolean authenticationFailure;
    private final boolean cancelled;

    public GitCloneException(String message, boolean authenticationFailure, boolean cancelled) {
        super(message);
        this.authenticationFailure = authenticationFailure;
        this.cancelled = cancelled;
    }

    public GitCloneException(String message, Throwable cause) {
        super(message, cause);
        this.authenticationFailure = false;
        this.cancelled = false;
    }

    public boolean isAuthenticationFailure() {
        return authenticationFailure;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
