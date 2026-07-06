package com.abbos.precisiontrigger.execution;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class ApplicationStateMachine {
    private static final Map<ApplicationState, Set<ApplicationState>> TRANSITIONS = Map.of(
            ApplicationState.IDLE, EnumSet.of(ApplicationState.SYNCING, ApplicationState.CANCELLED),
            ApplicationState.SYNCING, EnumSet.of(ApplicationState.READY, ApplicationState.FAILED),
            ApplicationState.READY, EnumSet.of(ApplicationState.ARMED, ApplicationState.SYNCING, ApplicationState.FAILED),
            ApplicationState.ARMED, EnumSet.of(ApplicationState.FINALIZING, ApplicationState.CANCELLED, ApplicationState.FAILED),
            ApplicationState.FINALIZING, EnumSet.of(ApplicationState.FIRING, ApplicationState.CANCELLED, ApplicationState.FAILED),
            ApplicationState.FIRING, EnumSet.of(ApplicationState.WAITING_ACK, ApplicationState.FAILED),
            ApplicationState.WAITING_ACK, EnumSet.of(ApplicationState.CONFIRMED, ApplicationState.FAILED),
            ApplicationState.CONFIRMED, EnumSet.of(ApplicationState.IDLE),
            ApplicationState.FAILED, EnumSet.of(ApplicationState.IDLE),
            ApplicationState.CANCELLED, EnumSet.of(ApplicationState.IDLE));

    private final AtomicReference<ApplicationState> current = new AtomicReference<>(ApplicationState.IDLE);

    public ApplicationState current() {
        return current.get();
    }

    public void transitionTo(ApplicationState next) {
        current.updateAndGet(existing -> {
            if (!TRANSITIONS.getOrDefault(existing, Set.of()).contains(next)) {
                throw new IllegalStateException("Invalid transition from " + existing + " to " + next);
            }
            return next;
        });
    }

    public void force(ApplicationState next) {
        current.set(next);
    }
}