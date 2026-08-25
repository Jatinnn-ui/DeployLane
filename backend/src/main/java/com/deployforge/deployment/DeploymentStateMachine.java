package com.deployforge.deployment;

import com.deployforge.common.error.Exceptions.InvalidStateException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The single authority on deployment status transitions.
 *
 * <p>Without this, status updates get scattered across pipeline steps and a retry or a race quietly
 * produces impossible history such as {@code FAILED -> READY}. Every write goes through
 * {@link #transition}, illegal moves throw, and the resulting error is a clean {@code 409} rather than
 * silent corruption.
 */
@Component
public class DeploymentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(DeploymentStateMachine.class);

    /** Terminal states every active state may fall into. */
    private static final Set<DeploymentStatus> ABORT_STATES =
            EnumSet.of(DeploymentStatus.FAILED, DeploymentStatus.CANCELLED);

    private static final Map<DeploymentStatus, Set<DeploymentStatus>> ALLOWED =
            new EnumMap<>(DeploymentStatus.class);

    static {
        // QUEUED -> STARTING is the rollback path: a previously built image is reused, so cloning,
        // detection and building are legitimately skipped. It stays a forward-only move.
        ALLOWED.put(DeploymentStatus.QUEUED, withAborts(DeploymentStatus.CLONING, DeploymentStatus.STARTING));
        ALLOWED.put(DeploymentStatus.CLONING, withAborts(DeploymentStatus.DETECTING));
        ALLOWED.put(
                DeploymentStatus.DETECTING,
                withAborts(DeploymentStatus.BUILDING, DeploymentStatus.IMAGE_BUILDING));
        ALLOWED.put(DeploymentStatus.BUILDING, withAborts(DeploymentStatus.IMAGE_BUILDING));
        ALLOWED.put(DeploymentStatus.IMAGE_BUILDING, withAborts(DeploymentStatus.STARTING));
        ALLOWED.put(DeploymentStatus.STARTING, withAborts(DeploymentStatus.HEALTH_CHECKING));
        ALLOWED.put(DeploymentStatus.HEALTH_CHECKING, withAborts(DeploymentStatus.READY));

        // Terminal states. READY may later be superseded (STOPPED) when a newer deployment is promoted.
        ALLOWED.put(DeploymentStatus.READY, EnumSet.of(DeploymentStatus.STOPPED));
        ALLOWED.put(DeploymentStatus.FAILED, EnumSet.noneOf(DeploymentStatus.class));
        ALLOWED.put(DeploymentStatus.CANCELLED, EnumSet.noneOf(DeploymentStatus.class));
        ALLOWED.put(DeploymentStatus.STOPPED, EnumSet.noneOf(DeploymentStatus.class));
    }

    private static Set<DeploymentStatus> withAborts(DeploymentStatus... next) {
        Set<DeploymentStatus> allowed = EnumSet.noneOf(DeploymentStatus.class);
        allowed.addAll(EnumSet.copyOf(java.util.Arrays.asList(next)));
        allowed.addAll(ABORT_STATES);
        return allowed;
    }

    public boolean canTransition(DeploymentStatus from, DeploymentStatus to) {
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(DeploymentStatus.class)).contains(to);
    }

    public Set<DeploymentStatus> allowedFrom(DeploymentStatus from) {
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(DeploymentStatus.class)));
    }

    /**
     * Applies a status change to the entity.
     *
     * @throws InvalidStateException when the transition is not permitted
     */
    public void transition(Deployment deployment, DeploymentStatus target) {
        DeploymentStatus current = deployment.getStatus();
        if (!canTransition(current, target)) {
            log.warn(
                    "invalid_deployment_transition deployment={} from={} to={}",
                    deployment.getId(),
                    current,
                    target);
            throw new InvalidStateException(
                    "A deployment cannot move from " + current + " to " + target);
        }
        deployment.applyStatus(target);
    }
}
