package com.abbos.precisiontrigger.action;

import com.abbos.precisiontrigger.planning.ExecutionPlan;

import java.time.Instant;

public final class DryRunActionExecutor implements ActionExecutor {
    @Override
    public ActionResult execute(ExecutionPlan executionPlan) {
        return new ActionResult(ActionOutcomeState.SENT, Instant.now(), "Dry-run action executed without external side effects");
    }
}