package com.deployforge.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deployforge.common.error.Exceptions.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The state machine is the guard against impossible deployment history, so its rules are tested
 * explicitly rather than implied by higher level tests.
 */
class DeploymentStateMachineTest {

    private final DeploymentStateMachine stateMachine = new DeploymentStateMachine();

    @ParameterizedTest
    @CsvSource({
        "QUEUED,CLONING",
        "CLONING,DETECTING",
        "DETECTING,BUILDING",
        "DETECTING,IMAGE_BUILDING",
        "BUILDING,IMAGE_BUILDING",
        "IMAGE_BUILDING,STARTING",
        "STARTING,HEALTH_CHECKING",
        "HEALTH_CHECKING,READY",
        "READY,STOPPED"
    })
    @DisplayName("the happy path and supersession are allowed")
    void allowsForwardTransitions(DeploymentStatus from, DeploymentStatus to) {
        assertThat(stateMachine.canTransition(from, to)).isTrue();
    }

    @Test
    @DisplayName("a rollback may skip straight from QUEUED to STARTING because it reuses an image")
    void allowsRollbackShortcut() {
        assertThat(stateMachine.canTransition(DeploymentStatus.QUEUED, DeploymentStatus.STARTING)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = DeploymentStatus.class,
            names = {"QUEUED", "CLONING", "DETECTING", "BUILDING", "IMAGE_BUILDING", "STARTING", "HEALTH_CHECKING"})
    @DisplayName("every active state can fail or be cancelled")
    void allowsAbortFromAnyActiveState(DeploymentStatus active) {
        assertThat(stateMachine.canTransition(active, DeploymentStatus.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(active, DeploymentStatus.CANCELLED)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "FAILED,READY",
        "READY,BUILDING",
        "CANCELLED,CLONING",
        "STOPPED,READY",
        "READY,HEALTH_CHECKING",
        "IMAGE_BUILDING,CLONING",
        "HEALTH_CHECKING,BUILDING"
    })
    @DisplayName("backwards and resurrecting transitions are rejected")
    void rejectsInvalidTransitions(DeploymentStatus from, DeploymentStatus to) {
        assertThat(stateMachine.canTransition(from, to)).isFalse();
    }

    @Test
    @DisplayName("a status cannot transition to itself")
    void rejectsSelfTransition() {
        for (DeploymentStatus status : DeploymentStatus.values()) {
            assertThat(stateMachine.canTransition(status, status)).isFalse();
        }
    }

    @Test
    @DisplayName("transition() applies the status and maintains timing fields")
    void transitionUpdatesEntity() {
        Deployment deployment = newDeployment();

        stateMachine.transition(deployment, DeploymentStatus.CLONING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.CLONING);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNull();

        stateMachine.transition(deployment, DeploymentStatus.DETECTING);
        stateMachine.transition(deployment, DeploymentStatus.FAILED);
        assertThat(deployment.getFinishedAt()).isNotNull();
        assertThat(deployment.getDurationMs()).isNotNull();
    }

    @Test
    @DisplayName("an illegal transition throws instead of corrupting history")
    void transitionRejectsIllegalMove() {
        Deployment deployment = newDeployment();
        stateMachine.transition(deployment, DeploymentStatus.CLONING);
        stateMachine.transition(deployment, DeploymentStatus.FAILED);

        assertThatThrownBy(() -> stateMachine.transition(deployment, DeploymentStatus.READY))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("cannot move from FAILED to READY");
    }

    @Test
    @DisplayName("terminal and cancellable classification matches the pipeline's expectations")
    void statusClassification() {
        assertThat(DeploymentStatus.READY.isTerminal()).isTrue();
        assertThat(DeploymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(DeploymentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(DeploymentStatus.STOPPED.isTerminal()).isTrue();
        assertThat(DeploymentStatus.BUILDING.isTerminal()).isFalse();

        assertThat(DeploymentStatus.QUEUED.isCancellable()).isTrue();
        assertThat(DeploymentStatus.HEALTH_CHECKING.isCancellable()).isTrue();
        assertThat(DeploymentStatus.READY.isCancellable()).isFalse();
        assertThat(DeploymentStatus.FAILED.isCancellable()).isFalse();
    }

    private Deployment newDeployment() {
        Deployment deployment =
                new Deployment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "main",
                        DeploymentTriggerType.MANUAL,
                        UUID.randomUUID());
        deployment.setId(UUID.randomUUID());
        return deployment;
    }
}
