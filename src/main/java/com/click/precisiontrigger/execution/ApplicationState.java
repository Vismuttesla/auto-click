package com.click.precisiontrigger.execution;

public enum ApplicationState {
    IDLE,
    SYNCING,
    READY,
    ARMED,
    FINALIZING,
    FIRING,
    WAITING_ACK,
    CONFIRMED,
    FAILED,
    CANCELLED
}