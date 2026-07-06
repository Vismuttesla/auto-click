package com.click.precisiontrigger.ui;

import com.click.precisiontrigger.action.ActionResult;
import com.click.precisiontrigger.auth.AuthStatusSnapshot;
import com.click.precisiontrigger.execution.ApplicationState;
import com.click.precisiontrigger.execution.ExecutionAuditRecord;
import com.click.precisiontrigger.execution.PrecisionReadinessSnapshot;
import com.click.precisiontrigger.planning.ExecutionPlan;
import com.click.precisiontrigger.sync.SyncStatusSnapshot;

import java.time.Duration;
import java.time.Instant;

public record UiSnapshot(
        ApplicationState applicationState,
        Instant estimatedServerTime,
        Duration selectedS1,
        Duration selectedS2,
        Duration currentRtt,
        Duration jitter,
        double confidence,
        Duration activeSyncInterval,
        Instant lastSync,
        Instant nextSync,
        AuthStatusSnapshot authStatus,
        SyncStatusSnapshot syncStatus,
        Instant targetTime,
        ExecutionPlan executionPlan,
        ActionResult actionResult,
        PrecisionReadinessSnapshot readiness,
        ExecutionAuditRecord lastExecutionAudit) {
}