package com.abbos.precisiontrigger.ui;

import com.abbos.precisiontrigger.action.ActionResult;
import com.abbos.precisiontrigger.auth.AuthStatusSnapshot;
import com.abbos.precisiontrigger.execution.ApplicationState;
import com.abbos.precisiontrigger.execution.ExecutionAuditRecord;
import com.abbos.precisiontrigger.execution.PrecisionReadinessSnapshot;
import com.abbos.precisiontrigger.planning.ExecutionPlan;
import com.abbos.precisiontrigger.sync.SyncStatusSnapshot;

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