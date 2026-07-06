package com.click.precisiontrigger.app;

import com.click.precisiontrigger.action.ActionExecutor;
import com.click.precisiontrigger.auth.RuntimeAuthTokenService;
import com.click.precisiontrigger.clock.NanoClock;
import com.click.precisiontrigger.clock.WallClock;
import com.click.precisiontrigger.config.ApplicationConfig;
import com.click.precisiontrigger.execution.PrecisionReadinessPolicy;
import com.click.precisiontrigger.latency.SampleWindow;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.logging.JsonlEventType;
import com.click.precisiontrigger.logsearch.LogSearchService;
import com.click.precisiontrigger.planning.ExecutionPlanCalculator;
import com.click.precisiontrigger.planning.ServerClockModel;
import com.click.precisiontrigger.planning.TargetTimeService;
import com.click.precisiontrigger.scheduler.HybridPrecisionScheduler;
import com.click.precisiontrigger.sync.DynamicSyncScheduler;
import com.click.precisiontrigger.sync.SyncCoordinator;
import com.click.precisiontrigger.sync.SyncIntervalService;

import java.util.Map;

public record ApplicationRuntime(
        ApplicationConfig applicationConfig,
        NanoClock nanoClock,
        WallClock wallClock,
        RuntimeAuthTokenService authTokenService,
        JsonlEventLogger eventLogger,
        LogSearchService logSearchService,
        SyncIntervalService syncIntervalService,
        SyncCoordinator syncCoordinator,
        DynamicSyncScheduler dynamicSyncScheduler,
        ServerClockModel serverClockModel,
        SampleWindow sampleWindow,
        TargetTimeService targetTimeService,
        ExecutionPlanCalculator executionPlanCalculator,
        PrecisionReadinessPolicy precisionReadinessPolicy,
        HybridPrecisionScheduler precisionScheduler,
        ActionExecutor actionExecutor) {

    public void shutdown() {
        eventLogger.log(JsonlEventType.APPLICATION_STOPPING, Map.of("reason", "APPLICATION_SHUTDOWN"));
        dynamicSyncScheduler.close();
        precisionScheduler.cancel();
        eventLogger.close();
    }
}
