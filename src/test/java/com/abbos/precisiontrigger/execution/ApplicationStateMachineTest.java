package com.abbos.precisiontrigger.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStateMachineTest {
    @Test
    void allowsValidTransitions() {
        ApplicationStateMachine stateMachine = new ApplicationStateMachine();

        stateMachine.transitionTo(ApplicationState.SYNCING);
        stateMachine.transitionTo(ApplicationState.READY);
        stateMachine.transitionTo(ApplicationState.ARMED);

        assertThat(stateMachine.current()).isEqualTo(ApplicationState.ARMED);
    }

    @Test
    void rejectsInvalidTransitions() {
        ApplicationStateMachine stateMachine = new ApplicationStateMachine();

        assertThatThrownBy(() -> stateMachine.transitionTo(ApplicationState.FIRING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transition");
    }
}