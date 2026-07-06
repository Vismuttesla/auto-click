package com.abbos.precisiontrigger.action;

public enum ActionOutcomeState {
    NOT_SENT,
    SENT,
    ACKNOWLEDGED,
    REJECTED,
    FAILED_BEFORE_SEND,
    AMBIGUOUS_TIMEOUT
}