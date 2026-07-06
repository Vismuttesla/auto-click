package com.click.precisiontrigger.action;

import com.click.precisiontrigger.planning.ExecutionPlan;

public interface ActionExecutor {
    ActionResult execute(ExecutionPlan executionPlan);
}