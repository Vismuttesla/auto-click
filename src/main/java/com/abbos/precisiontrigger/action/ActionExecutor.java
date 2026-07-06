package com.abbos.precisiontrigger.action;

import com.abbos.precisiontrigger.planning.ExecutionPlan;

public interface ActionExecutor {
    ActionResult execute(ExecutionPlan executionPlan);
}