package com.abbos.precisiontrigger.app;

import com.abbos.precisiontrigger.action.ActionExecutor;
import com.abbos.precisiontrigger.auth.RuntimeAuthTokenService;
import com.abbos.precisiontrigger.clock.NanoClock;
import com.abbos.precisiontrigger.clock.WallClock;
import com.abbos.precisiontrigger.config.ApplicationConfig;
import com.abbos.precisiontrigger.execution.PrecisionReadinessPolicy;
import com.abbos.precisiontrigger.latency.SampleWindow;
import com.abbos.precisiontrigger.logging.JsonlEventLogger;
import com.abbos.precisiontrigger.logging.JsonlEventType;
import com.abbos.precisiontrigger.logsearch.LogSearchService;
import com.abbos.precisiontrigger.planning.ExecutionPlanCalculator;
import com.abbos.precisiontrigger.planning.ServerClockModel;
import com.abbos.precisiontrigger.planning.TargetTimeService;
import com.abbos.precisiontrigger.scheduler.HybridPrecisionScheduler;
import com.abbos.precisiontrigger.sync.DynamicSyncScheduler;
import com.abbos.precisiontrigger.sync.SyncCoordinator;
import com.abbos.precisiontrigger.sync.SyncIntervalService;

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
