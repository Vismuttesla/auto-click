package com.click.precisiontrigger.execution;

import com.click.precisiontrigger.action.ActionOutcomeState;

import java.time.Instant;

public record ExecutionAuditRecord(
        Instant targetServerTime,
        long selectedS1Nanos,
        long selectedS2Nanos,
        long sourceSampleSequence,
        long sourceClockVersion,
        long sourcePlanVersion,
        long configurationVersion,
        long executionOverheadNanos,
        Instant desiredFireServerTime,
        long plannedLocalDeadlineNano,
        long actualFireNano,
        long schedulerErrorNanos,
        ActionOutcomeState actionOutcome,
        String acknowledgment,
        String ambiguityReason,
        Instant recordedAt) {
}