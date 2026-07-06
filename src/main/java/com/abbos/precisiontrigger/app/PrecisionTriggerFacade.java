package com.abbos.precisiontrigger.app;

import com.abbos.precisiontrigger.action.ActionOutcomeState;
import com.abbos.precisiontrigger.action.ActionResult;
import com.abbos.precisiontrigger.execution.ApplicationState;
import com.abbos.precisiontrigger.execution.ApplicationStateMachine;
import com.abbos.precisiontrigger.execution.ExecutionAuditRecord;
import com.abbos.precisiontrigger.execution.OneShotGuard;
import com.abbos.precisiontrigger.execution.PrecisionReadinessSnapshot;
import com.abbos.precisiontrigger.latency.LatencySample;
import com.abbos.precisiontrigger.logging.JsonlEventType;
import com.abbos.precisiontrigger.logsearch.LogIndexEntry;
import com.abbos.precisiontrigger.logsearch.LogSearchOrder;
import com.abbos.precisiontrigger.logsearch.LogSearchQuery;
import com.abbos.precisiontrigger.network.NetworkChangeDetector;
import com.abbos.precisiontrigger.network.NetworkEnvironmentMonitor;
import com.abbos.precisiontrigger.network.RuntimeHardeningAdapter;
import com.abbos.precisiontrigger.network.RuntimeHardeningEvent;
import com.abbos.precisiontrigger.network.RuntimeHardeningHooks;
import com.abbos.precisiontrigger.network.RuntimeHardeningResult;
import com.abbos.precisiontrigger.network.RuntimeHardeningSignalSink;
import com.abbos.precisiontrigger.planning.ExecutionPlan;
import com.abbos.precisiontrigger.planning.ServerClockSnapshot;
import com.abbos.precisiontrigger.sync.SyncStatus;
import com.abbos.precisiontrigger.ui.UiSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PrecisionTriggerFacade {
    private static final Duration NETWORK_CHANGE_SUPPRESSION_WINDOW = Duration.ofSeconds(10);
    private static final Duration SYSTEM_RESUME_SUPPRESSION_WINDOW = Duration.ofSeconds(30);
    private static final Duration NETWORK_MONITOR_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Set<String> TIMING_EVENT_TYPES = Set.of(
            JsonlEventType.TIME_SYNC_STARTED.name(),
            JsonlEventType.TIME_SYNC_SAMPLE.name(),
            JsonlEventType.TIME_SYNC_FAILED.name(),
            JsonlEventType.LATENCY_ESTIMATE_UPDATED.name(),
            JsonlEventType.SERVER_CLOCK_UPDATED.name(),
            JsonlEventType.MANUAL_SYNC_REQUESTED.name(),
            JsonlEventType.MANUAL_SYNC_STARTED.name(),
            JsonlEventType.MANUAL_SYNC_COMPLETED.name(),
            JsonlEventType.MANUAL_SYNC_SKIPPED.name(),
            JsonlEventType.PERIODIC_SYNC_SKIPPED.name());
    private static final Set<String> EXECUTION_EVENT_TYPES = Set.of(
            JsonlEventType.EXECUTION_PLAN_UPDATED.name(),
            JsonlEventType.FINAL_WINDOW_ENTERED.name(),
            JsonlEventType.ACTION_FIRE_STARTED.name(),
            JsonlEventType.ACTION_FIRED.name(),
            JsonlEventType.ACTION_ACKNOWLEDGED.name(),
            JsonlEventType.ACTION_REJECTED.name(),
            JsonlEventType.ACTION_FAILED.name(),
            JsonlEventType.ACTION_AMBIGUOUS.name());

    private final ApplicationRuntime runtime;
    private final ApplicationStateMachine stateMachine;
    private final OneShotGuard oneShotGuard;
    private final Object planLock = new Object();
    private final RuntimeHardeningSignalSink runtimeHardeningSignalSink;
    private final NetworkEnvironmentMonitor networkEnvironmentMonitor;
    private volatile ExecutionPlan lastPlan;
    private volatile ActionResult lastActionResult;
    private volatile ExecutionAuditRecord lastExecutionAudit;
    private volatile boolean rearmAfterHardening;

    public PrecisionTriggerFacade(ApplicationRuntime runtime) {
        this.runtime = runtime;
        this.stateMachine = new ApplicationStateMachine();
        this.oneShotGuard = new OneShotGuard();
        this.runtimeHardeningSignalSink = new RuntimeHardeningAdapter(
                new RuntimeHardeningHooks() {
                    @Override
                    public void beforeRefresh(RuntimeHardeningEvent event) {
                        runtime.eventLogger().log(JsonlEventType.NETWORK_ENVIRONMENT_CHANGED, Map.of(
                                "hardeningEventType", event.type().name(),
                                "source", event.source(),
                                "attributes", event.attributes()));
                        prepareForFreshMeasurements();
                    }

                    @Override
                    public void afterRefresh(RuntimeHardeningEvent event, SyncStatus status) {
                        maybeRearmAfterRefresh(status);
                    }
                },
                event -> PrecisionTriggerFacade.this.runtime.dynamicSyncScheduler().triggerManualSync(),
                NETWORK_CHANGE_SUPPRESSION_WINDOW,
                SYSTEM_RESUME_SUPPRESSION_WINDOW);
        this.networkEnvironmentMonitor = new NetworkEnvironmentMonitor(
                runtime.wallClock(),
                runtimeHardeningSignalSink,
                NETWORK_MONITOR_POLL_INTERVAL,
                new NetworkChangeDetector(),
                NetworkEnvironmentMonitor.systemSnapshotSupplier());
        this.runtime.dynamicSyncScheduler().start();
        this.networkEnvironmentMonitor.start();
    }

    public UiSnapshot snapshot() {
        Instant now = runtime.wallClock().now();
        Instant serverNow = runtime.serverClockModel().now();
        ServerClockSnapshot clockSnapshot = runtime.serverClockModel().snapshot();
        PrecisionReadinessSnapshot readiness = runtime.precisionReadinessPolicy().evaluate(clockSnapshot, now);
        var syncStatus = runtime.syncCoordinator().status();
        var authStatus = runtime.authTokenService().status();
        return new UiSnapshot(
                stateMachine.current(),
                serverNow,
                clockSnapshot == null ? null : clockSnapshot.estimatedS1(),
                clockSnapshot == null ? null : clockSnapshot.estimatedS2(),
                latestRtt(),
                clockSnapshot == null ? null : clockSnapshot.jitter(),
                clockSnapshot == null ? 0.0 : clockSnapshot.confidence(),
                runtime.syncIntervalService().currentInterval(),
                syncStatus.lastSuccessful(),
                syncStatus.nextPlanned(),
                authStatus,
                syncStatus,
                runtime.targetTimeService().currentTarget(),
                lastPlan,
                lastActionResult,
                readiness,
                lastExecutionAudit);
    }

    public void applyToken(String rawToken) {
        runtime.authTokenService().applyToken(rawToken, runtime.wallClock().now());
    }

    public void clearToken() {
        runtime.authTokenService().clearToken(runtime.wallClock().now());
    }

    public SyncStatus testAuthentication() {
        SyncStatus status = runtime.dynamicSyncScheduler().triggerAuthTest();
        if (status == SyncStatus.SUCCESS) {
            runtime.authTokenService().markAuthenticated(runtime.wallClock().now());
        }
        updateStateAfterSync(status);
        return status;
    }

    public SyncStatus syncNow() {
        stateMachine.force(ApplicationState.SYNCING);
        SyncStatus status = runtime.dynamicSyncScheduler().triggerManualSync();
        updateStateAfterSync(status);
        return status;
    }

    public void applySyncInterval(Duration duration) {
        runtime.dynamicSyncScheduler().applyNewInterval(duration);
    }

    public void setTarget(LocalDate date, LocalTime time, ZoneId zoneId) {
        runtime.targetTimeService().setTarget(date.atTime(time).atZone(zoneId).toInstant());
    }

    public void armTarget() {
        synchronized (planLock) {
            scheduleCurrentTarget();
        }
    }

    public RuntimeHardeningResult signalNetworkEnvironmentChanged(String source, Map<String, String> attributes) {
        return runtimeHardeningSignalSink.networkEnvironmentChanged(runtime.wallClock().now(), source, attributes);
    }

    public RuntimeHardeningResult signalSystemResumed(String source, Duration observedGap, Map<String, String> attributes) {
        return runtimeHardeningSignalSink.systemResumed(runtime.wallClock().now(), source, observedGap, attributes);
    }

    public String renderTimingHistory(int limit) {
        return formatEntries(filterEntries(TIMING_EVENT_TYPES, limit), "No timing history yet.");
    }

    public String renderExecutionAudit(int limit) {
        String history = formatEntries(filterEntries(EXECUTION_EVENT_TYPES, limit), "No execution audit yet.");
        if (lastExecutionAudit == null) {
            return history;
        }
        return history + System.lineSeparator() + System.lineSeparator() + "Last execution: " + lastExecutionAudit;
    }

    public void cancelArm() {
        synchronized (planLock) {
            runtime.precisionScheduler().cancel();
            oneShotGuard.reset();
            rearmAfterHardening = false;
            lastPlan = null;
            stateMachine.force(ApplicationState.CANCELLED);
        }
    }

    public void shutdown() {
        networkEnvironmentMonitor.close();
        runtime.shutdown();
    }

    private void updateStateAfterSync(SyncStatus status) {
        if (status == SyncStatus.SUCCESS) {
            PrecisionReadinessSnapshot readiness = runtime.precisionReadinessPolicy().evaluate(runtime.serverClockModel().snapshot(), runtime.wallClock().now());
            stateMachine.force(readiness.ready() ? ApplicationState.READY : ApplicationState.IDLE);
        } else if (status == SyncStatus.FAILED) {
            stateMachine.force(ApplicationState.FAILED);
        }
    }

    private Duration latestRtt() {
        List<LatencySample> samples = runtime.sampleWindow().snapshot().samples();
        return samples.isEmpty() ? null : samples.getLast().rtt();
    }

    private void prepareForFreshMeasurements() {
        synchronized (planLock) {
            runtime.serverClockModel().markPotentiallyStale(runtime.wallClock().now());
            rearmAfterHardening = hasActiveScheduledPlan();
            if (rearmAfterHardening) {
                runtime.precisionScheduler().cancel();
                oneShotGuard.reset();
                lastPlan = null;
                stateMachine.force(ApplicationState.CANCELLED);
            }
        }
    }

    private void maybeRearmAfterRefresh(SyncStatus status) {
        synchronized (planLock) {
            if (!rearmAfterHardening) {
                updateStateAfterSync(status);
                return;
            }
            rearmAfterHardening = false;
            if (status == SyncStatus.SUCCESS && runtime.targetTimeService().currentTarget() != null) {
                scheduleCurrentTarget();
            } else {
                updateStateAfterSync(status);
            }
        }
    }

    private boolean hasActiveScheduledPlan() {
        return switch (stateMachine.current()) {
            case ARMED, FINALIZING -> true;
            default -> false;
        };
    }

    private void scheduleCurrentTarget() {
        List<LatencySample> samples = runtime.sampleWindow().snapshot().samples();
        if (samples.isEmpty()) {
            throw new IllegalStateException("No latency sample is available yet");
        }
        Instant target = runtime.targetTimeService().currentTarget();
        if (target == null) {
            throw new IllegalStateException("Target time is not set");
        }
        ServerClockSnapshot clockSnapshot = runtime.serverClockModel().snapshot();
        PrecisionReadinessSnapshot readiness = runtime.precisionReadinessPolicy().evaluate(clockSnapshot, runtime.wallClock().now());
        if (!readiness.ready()) {
            throw new IllegalStateException("Precision readiness failed: " + readiness.reason());
        }
        Instant serverNow = runtime.serverClockModel().now();
        if (serverNow == null || !target.isAfter(serverNow)) {
            throw new IllegalStateException("Target time must be in the future relative to the current server clock");
        }

        runtime.precisionScheduler().cancel();
        oneShotGuard.reset();
        lastActionResult = null;

        LatencySample latest = samples.getLast();
        stateMachine.force(ApplicationState.ARMED);
        lastPlan = runtime.executionPlanCalculator().calculate(
                target,
                latest,
                runtime.syncIntervalService().settings().configurationVersion(),
                runtime.wallClock().now());
        runtime.eventLogger().log(JsonlEventType.EXECUTION_PLAN_UPDATED, planMetadata(lastPlan));
        runtime.eventLogger().log(JsonlEventType.FINAL_WINDOW_ENTERED, Map.ofEntries(
                Map.entry("targetServerTime", target.toString()),
                Map.entry("planVersion", lastPlan.planVersion()),
                Map.entry("configurationVersion", lastPlan.configurationVersion()),
                Map.entry("finalFreezeWindowNanos", runtime.applicationConfig().timing().finalFreezeWindow().toNanos())));
        stateMachine.force(ApplicationState.FINALIZING);
        ExecutionPlan frozenPlan = lastPlan;
        runtime.precisionScheduler().schedule(frozenPlan.localDeadlineNano(), () -> firePlan(frozenPlan));
    }

    private void firePlan(ExecutionPlan plan) {
        if (!oneShotGuard.tryFire()) {
            return;
        }
        stateMachine.force(ApplicationState.FIRING);
        long actualFireNano = runtime.nanoClock().nanoTime();
        runtime.eventLogger().log(JsonlEventType.ACTION_FIRE_STARTED, Map.ofEntries(
                Map.entry("planVersion", plan.planVersion()),
                Map.entry("targetServerTime", plan.targetServerTime().toString()),
                Map.entry("plannedLocalDeadlineNano", plan.localDeadlineNano()),
                Map.entry("actualFireNano", actualFireNano)));
        ActionResult actionResult = runtime.actionExecutor().execute(plan);
        lastActionResult = actionResult;
        long schedulerErrorNanos = actualFireNano - plan.localDeadlineNano();
        lastExecutionAudit = new ExecutionAuditRecord(
                plan.targetServerTime(),
                plan.selectedS1().toNanos(),
                plan.selectedS2().toNanos(),
                plan.sourceSampleSequence(),
                plan.clockVersion(),
                plan.planVersion(),
                plan.configurationVersion(),
                plan.executionOverhead().toNanos(),
                plan.desiredFireServerTime(),
                plan.localDeadlineNano(),
                actualFireNano,
                schedulerErrorNanos,
                actionResult.outcomeState(),
                actionAcknowledgement(actionResult),
                actionAmbiguity(actionResult),
                runtime.wallClock().now());
        runtime.eventLogger().log(JsonlEventType.ACTION_FIRED, executionMetadata(lastExecutionAudit, actionResult));
        JsonlEventType outcomeEventType = outcomeEventType(actionResult.outcomeState());
        runtime.eventLogger().log(outcomeEventType, executionMetadata(lastExecutionAudit, actionResult));
        transitionAfterAction(actionResult.outcomeState());
    }

    private void transitionAfterAction(ActionOutcomeState outcomeState) {
        switch (outcomeState) {
            case ACKNOWLEDGED, SENT -> {
                stateMachine.force(ApplicationState.WAITING_ACK);
                stateMachine.force(ApplicationState.CONFIRMED);
            }
            case REJECTED, FAILED_BEFORE_SEND, AMBIGUOUS_TIMEOUT, NOT_SENT -> stateMachine.force(ApplicationState.FAILED);
        }
    }

    private static JsonlEventType outcomeEventType(ActionOutcomeState outcomeState) {
        return switch (outcomeState) {
            case ACKNOWLEDGED -> JsonlEventType.ACTION_ACKNOWLEDGED;
            case REJECTED -> JsonlEventType.ACTION_REJECTED;
            case AMBIGUOUS_TIMEOUT -> JsonlEventType.ACTION_AMBIGUOUS;
            case FAILED_BEFORE_SEND, NOT_SENT -> JsonlEventType.ACTION_FAILED;
            case SENT -> JsonlEventType.ACTION_FIRED;
        };
    }

    private static String actionAcknowledgement(ActionResult actionResult) {
        return switch (actionResult.outcomeState()) {
            case ACKNOWLEDGED, SENT -> actionResult.diagnostic();
            default -> null;
        };
    }

    private static String actionAmbiguity(ActionResult actionResult) {
        return actionResult.outcomeState() == ActionOutcomeState.AMBIGUOUS_TIMEOUT ? actionResult.diagnostic() : null;
    }

    private Map<String, Object> planMetadata(ExecutionPlan plan) {
        return Map.ofEntries(
                Map.entry("targetServerTime", plan.targetServerTime().toString()),
                Map.entry("desiredFireServerTime", plan.desiredFireServerTime().toString()),
                Map.entry("plannedLocalDeadlineNano", plan.localDeadlineNano()),
                Map.entry("selectedS1Nanos", plan.selectedS1().toNanos()),
                Map.entry("selectedS2Nanos", plan.selectedS2().toNanos()),
                Map.entry("executionOverheadNanos", plan.executionOverhead().toNanos()),
                Map.entry("jitterNanos", plan.jitter().toNanos()),
                Map.entry("confidence", plan.confidence()),
                Map.entry("sourceSampleSequence", plan.sourceSampleSequence()),
                Map.entry("clockVersion", plan.clockVersion()),
                Map.entry("planVersion", plan.planVersion()),
                Map.entry("configurationVersion", plan.configurationVersion()));
    }

    private static Map<String, Object> executionMetadata(ExecutionAuditRecord auditRecord, ActionResult actionResult) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetServerTime", auditRecord.targetServerTime().toString());
        metadata.put("selectedS1Nanos", auditRecord.selectedS1Nanos());
        metadata.put("selectedS2Nanos", auditRecord.selectedS2Nanos());
        metadata.put("sourceSampleSequence", auditRecord.sourceSampleSequence());
        metadata.put("sourceClockVersion", auditRecord.sourceClockVersion());
        metadata.put("sourcePlanVersion", auditRecord.sourcePlanVersion());
        metadata.put("configurationVersion", auditRecord.configurationVersion());
        metadata.put("executionOverheadNanos", auditRecord.executionOverheadNanos());
        metadata.put("desiredFireServerTime", auditRecord.desiredFireServerTime().toString());
        metadata.put("plannedLocalDeadlineNano", auditRecord.plannedLocalDeadlineNano());
        metadata.put("actualFireNano", auditRecord.actualFireNano());
        metadata.put("schedulerErrorNanos", auditRecord.schedulerErrorNanos());
        metadata.put("actionOutcome", auditRecord.actionOutcome().name());
        metadata.put("acknowledgment", String.valueOf(auditRecord.acknowledgment()));
        metadata.put("ambiguityReason", String.valueOf(auditRecord.ambiguityReason()));
        metadata.put("diagnostic", String.valueOf(actionResult.diagnostic()));
        if (actionResult.firedAt() != null) {
            metadata.put("firedAt", actionResult.firedAt().toString());
        }
        return metadata;
    }

    private List<LogIndexEntry> filterEntries(Set<String> eventTypes, int limit) {
        int cappedLimit = Math.max(1, limit);
        return runtime.logSearchService().search(new LogSearchQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LogSearchOrder.NEWEST_FIRST,
                        0,
                        Math.max(50, cappedLimit * 4)))
                .entries().stream()
                .filter(entry -> eventTypes.contains(entry.eventType()))
                .limit(cappedLimit)
                .toList();
    }

    private static String formatEntries(List<LogIndexEntry> entries, String emptyMessage) {
        if (entries.isEmpty()) {
            return emptyMessage;
        }
        return entries.stream()
                .map(entry -> entry.timestamp() + " | " + entry.eventType() + " | " + entry.rawLine())
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }
}
