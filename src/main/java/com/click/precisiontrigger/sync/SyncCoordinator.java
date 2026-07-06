package com.click.precisiontrigger.sync;

import com.click.precisiontrigger.client.ServerTimeClient;
import com.click.precisiontrigger.client.ServerTimeClientResult;
import com.click.precisiontrigger.latency.LatencyEstimator;
import com.click.precisiontrigger.latency.LatencySample;
import com.click.precisiontrigger.latency.SampleWindow;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.logging.JsonlEventType;
import com.click.precisiontrigger.planning.ServerClockModel;
import com.click.precisiontrigger.planning.ServerClockSnapshot;
import com.click.precisiontrigger.time.TimeMeasurement;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncCoordinator {
    private final ServerTimeClient serverTimeClient;
    private final LatencyEstimator latencyEstimator;
    private final SampleWindow sampleWindow;
    private final ServerClockModel serverClockModel;
    private final JsonlEventLogger eventLogger;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicReference<SyncStatusSnapshot> status = new AtomicReference<>(new SyncStatusSnapshot(SyncStatus.IDLE, null, null, null, null, null));

    public SyncCoordinator(ServerTimeClient serverTimeClient,
                           LatencyEstimator latencyEstimator,
                           SampleWindow sampleWindow,
                           ServerClockModel serverClockModel,
                           JsonlEventLogger eventLogger) {
        this.serverTimeClient = Objects.requireNonNull(serverTimeClient, "serverTimeClient");
        this.latencyEstimator = Objects.requireNonNull(latencyEstimator, "latencyEstimator");
        this.sampleWindow = Objects.requireNonNull(sampleWindow, "sampleWindow");
        this.serverClockModel = Objects.requireNonNull(serverClockModel, "serverClockModel");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger");
    }

    public SyncStatusSnapshot status() {
        return status.get();
    }

    public SyncStatus runSync(SyncTrigger trigger, Duration activeSyncInterval, long configurationVersion, Instant startedAt) {
        if (!running.compareAndSet(false, true)) {
            JsonlEventType eventType = trigger == SyncTrigger.PERIODIC ? JsonlEventType.PERIODIC_SYNC_SKIPPED : JsonlEventType.MANUAL_SYNC_SKIPPED;
            eventLogger.log(eventType, Map.of("reason", "ALREADY_RUNNING", "trigger", trigger.name()));
            status.set(new SyncStatusSnapshot(SyncStatus.SKIPPED_ALREADY_RUNNING, status.get().lastStarted(), status.get().lastCompleted(), status.get().lastSuccessful(), status.get().nextPlanned(), "ALREADY_RUNNING"));
            return SyncStatus.SKIPPED_ALREADY_RUNNING;
        }
        try {
            if (trigger == SyncTrigger.MANUAL) {
                eventLogger.log(JsonlEventType.MANUAL_SYNC_REQUESTED, Map.of("configurationVersion", configurationVersion));
                eventLogger.log(JsonlEventType.MANUAL_SYNC_STARTED, Map.of("configurationVersion", configurationVersion));
            }
            status.set(new SyncStatusSnapshot(SyncStatus.SYNCING, startedAt, status.get().lastCompleted(), status.get().lastSuccessful(), status.get().nextPlanned(), null));
            ServerTimeClientResult result = serverTimeClient.fetchServerTime(requestSequence.incrementAndGet(), configurationVersion);
            if (!result.success()) {
                eventLogger.log(JsonlEventType.TIME_SYNC_FAILED, Map.of(
                        "failureCode", result.failureCode().name(),
                        "message", result.safeMessage(),
                        "trigger", trigger.name(),
                        "configurationVersion", configurationVersion));
                status.set(new SyncStatusSnapshot(SyncStatus.FAILED, startedAt, Instant.now(), status.get().lastSuccessful(), status.get().nextPlanned(), result.safeMessage()));
                return SyncStatus.FAILED;
            }

            TimeMeasurement measurement = result.measurement();
            LatencySample sample = latencyEstimator.estimate(measurement, sampleWindow.snapshot(), activeSyncInterval);
            sampleWindow.add(sample);
            serverClockModel.updateFromSample(sample, Instant.now());
            ServerClockSnapshot clockSnapshot = serverClockModel.snapshot();

            eventLogger.log(JsonlEventType.TIME_SYNC_SAMPLE, sampleMetadata(sample, measurement, clockSnapshot));
            eventLogger.log(JsonlEventType.LATENCY_ESTIMATE_UPDATED, Map.ofEntries(
                    Map.entry("sequence", sample.sequence()),
                    Map.entry("estimationStrategy", sample.estimationStrategy()),
                    Map.entry("qualityScore", sample.qualityScore()),
                    Map.entry("confidence", sample.confidence()),
                    Map.entry("accepted", sample.accepted()),
                    Map.entry("rejectionReason", String.valueOf(sample.rejectionReason()))));
            if (clockSnapshot != null) {
                eventLogger.log(JsonlEventType.SERVER_CLOCK_UPDATED, Map.ofEntries(
                        Map.entry("clockVersion", clockSnapshot.version()),
                        Map.entry("sourceSampleSequence", clockSnapshot.sourceSampleSequence()),
                        Map.entry("confidence", clockSnapshot.confidence()),
                        Map.entry("estimatedServerTimeAtAnchor", clockSnapshot.estimatedServerTimeAtAnchor().toString()),
                        Map.entry("estimatedS1Nanos", clockSnapshot.estimatedS1().toNanos()),
                        Map.entry("estimatedS2Nanos", clockSnapshot.estimatedS2().toNanos())));
            }
            Instant completedAt = Instant.now();
            status.set(new SyncStatusSnapshot(SyncStatus.SUCCESS, startedAt, completedAt, completedAt, status.get().nextPlanned(), null));
            if (trigger == SyncTrigger.MANUAL) {
                eventLogger.log(JsonlEventType.MANUAL_SYNC_COMPLETED, Map.of("configurationVersion", configurationVersion));
            }
            return SyncStatus.SUCCESS;
        } finally {
            running.set(false);
        }
    }

    public void updateNextPlanned(Instant nextPlanned) {
        SyncStatusSnapshot current = status.get();
        status.set(new SyncStatusSnapshot(current.status(), current.lastStarted(), current.lastCompleted(), current.lastSuccessful(), nextPlanned, current.lastFailure()));
    }

    private static Map<String, Object> sampleMetadata(LatencySample sample, TimeMeasurement measurement, ServerClockSnapshot clockSnapshot) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sequence", sample.sequence());
        metadata.put("localCollectedAt", sample.localCollectedAt().toString());
        metadata.put("serverTimestamp", sample.serverTimestamp().toString());
        metadata.put("estimatedServerTimeAtReceive", sample.estimatedServerTimeAtReceive().toString());
        metadata.put("localSendNano", measurement.localSendNano());
        metadata.put("localReceiveNano", measurement.localReceiveNano());
        metadata.put("s1Nanos", sample.s1().toNanos());
        metadata.put("s2Nanos", sample.s2().toNanos());
        metadata.put("rttNanos", sample.rtt().toNanos());
        metadata.put("jitterNanos", sample.jitter().toNanos());
        metadata.put("estimationStrategy", sample.estimationStrategy());
        metadata.put("qualityScore", sample.qualityScore());
        metadata.put("confidence", sample.confidence());
        metadata.put("accepted", sample.accepted());
        metadata.put("rejectionReason", String.valueOf(sample.rejectionReason()));
        metadata.put("activeSyncIntervalNanos", sample.activeSyncInterval().toNanos());
        metadata.put("configurationVersion", sample.configurationVersion());
        metadata.put("httpStatus", measurement.httpStatus());
        metadata.put("rawTimestamp", measurement.rawTimestamp().toPlainString());
        if (clockSnapshot != null) {
            metadata.put("clockVersion", clockSnapshot.version());
        }
        return metadata;
    }
}
