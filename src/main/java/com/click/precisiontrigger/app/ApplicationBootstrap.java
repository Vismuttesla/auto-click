package com.click.precisiontrigger.app;

import com.click.precisiontrigger.action.DryRunActionExecutor;
import com.click.precisiontrigger.auth.JwtMetadataInspector;
import com.click.precisiontrigger.auth.RuntimeAuthTokenService;
import com.click.precisiontrigger.checktime.CheckTimeV2ResponseInterpreter;
import com.click.precisiontrigger.client.HttpServerTimeClient;
import com.click.precisiontrigger.clock.SystemNanoClock;
import com.click.precisiontrigger.clock.SystemWallClock;
import com.click.precisiontrigger.config.ApplicationConfigLoader;
import com.click.precisiontrigger.config.FileBackedRuntimeSettingsRepository;
import com.click.precisiontrigger.execution.PrecisionReadinessPolicy;
import com.click.precisiontrigger.latency.HistoricalCalibratedStrategy;
import com.click.precisiontrigger.latency.JitterEstimator;
import com.click.precisiontrigger.latency.MadOutlierFilter;
import com.click.precisiontrigger.latency.RobustLatencyEstimator;
import com.click.precisiontrigger.latency.SampleQualityScorer;
import com.click.precisiontrigger.latency.SampleWindow;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.logging.JsonlEventType;
import com.click.precisiontrigger.logsearch.FileBackedLogRepository;
import com.click.precisiontrigger.logsearch.LogIndexBuilder;
import com.click.precisiontrigger.logsearch.LogSearchService;
import com.click.precisiontrigger.network.DefaultAuthorizationHeaderProvider;
import com.click.precisiontrigger.planning.ExecutionPlanCalculator;
import com.click.precisiontrigger.planning.FixedActionOverheadEstimator;
import com.click.precisiontrigger.planning.ServerClockModel;
import com.click.precisiontrigger.planning.TargetTimeService;
import com.click.precisiontrigger.scheduler.HybridPrecisionScheduler;
import com.click.precisiontrigger.sync.DynamicSyncScheduler;
import com.click.precisiontrigger.sync.SyncCoordinator;
import com.click.precisiontrigger.sync.SyncIntervalService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public final class ApplicationBootstrap {
    public ApplicationRuntime bootstrap() throws IOException {
        ApplicationConfigLoader.LoadedConfiguration loaded = new ApplicationConfigLoader().load();
        FileBackedRuntimeSettingsRepository runtimeSettingsRepository = new FileBackedRuntimeSettingsRepository(
                Path.of("config", "runtime-settings.json"),
                loaded.jsonMapper());
        var runtimeSettings = runtimeSettingsRepository.load();
        var nanoClock = new SystemNanoClock();
        var wallClock = new SystemWallClock();
        var eventLogger = new JsonlEventLogger(
                loaded.applicationConfig().logging().directory(),
                loaded.applicationConfig().logging().asyncQueueCapacity());
        var logSearchService = new LogSearchService(new FileBackedLogRepository(loaded.applicationConfig().logging().directory(), new LogIndexBuilder()));
        var authTokenService = new RuntimeAuthTokenService(new JwtMetadataInspector(loaded.jsonMapper()), eventLogger, loaded.applicationConfig().auth());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(loaded.applicationConfig().server().connectTimeout())
                .build();
        CheckTimeV2ResponseInterpreter interpreter = HttpServerTimeClient.defaultInterpreter(loaded.jsonMapper(), loaded.applicationConfig().server());
        var serverTimeClient = new HttpServerTimeClient(
                httpClient,
                loaded.applicationConfig().server(),
                loaded.applicationConfig().request(),
                new DefaultAuthorizationHeaderProvider(authTokenService),
                authTokenService,
                interpreter,
                nanoClock,
                wallClock,
                eventLogger);
        var sampleWindow = new SampleWindow(loaded.applicationConfig().timing().sampleWindowSize());
        var serverClockModel = new ServerClockModel(nanoClock);
        var latencyEstimator = new RobustLatencyEstimator(
                new HistoricalCalibratedStrategy(),
                new MadOutlierFilter(),
                new JitterEstimator(),
                new SampleQualityScorer(),
                loaded.applicationConfig().server().timestamp().semantics());
        var syncCoordinator = new SyncCoordinator(serverTimeClient, latencyEstimator, sampleWindow, serverClockModel, eventLogger);
        var syncIntervalService = new SyncIntervalService(loaded.applicationConfig().timing(), runtimeSettingsRepository, eventLogger, runtimeSettings);
        var dynamicSyncScheduler = new DynamicSyncScheduler(syncIntervalService, syncCoordinator, wallClock);
        var targetTimeService = new TargetTimeService();
        var executionPlanCalculator = new ExecutionPlanCalculator(
                serverClockModel,
                new FixedActionOverheadEstimator(loaded.applicationConfig().timing().defaultActionOverhead()),
                nanoClock);
        var precisionScheduler = new HybridPrecisionScheduler(
                nanoClock,
                loaded.applicationConfig().timing().coarseThreshold().toNanos(),
                loaded.applicationConfig().timing().spinThreshold().toNanos());
        eventLogger.log(JsonlEventType.APPLICATION_STARTED, Map.of("timestamp", Instant.now().toString()));
        return new ApplicationRuntime(
                loaded.applicationConfig(),
                nanoClock,
                wallClock,
                authTokenService,
                eventLogger,
                logSearchService,
                syncIntervalService,
                syncCoordinator,
                dynamicSyncScheduler,
                serverClockModel,
                sampleWindow,
                targetTimeService,
                executionPlanCalculator,
                new PrecisionReadinessPolicy(loaded.applicationConfig().timing()),
                precisionScheduler,
                new DryRunActionExecutor());
    }
}
