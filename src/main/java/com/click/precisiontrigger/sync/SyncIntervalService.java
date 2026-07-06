package com.click.precisiontrigger.sync;

import com.click.precisiontrigger.config.RuntimeSettings;
import com.click.precisiontrigger.config.RuntimeSettingsRepository;
import com.click.precisiontrigger.config.TimingConfig;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.logging.JsonlEventType;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncIntervalService {
    private final TimingConfig timingConfig;
    private final RuntimeSettingsRepository runtimeSettingsRepository;
    private final JsonlEventLogger eventLogger;
    private final AtomicReference<RuntimeSettings> currentSettings;

    public SyncIntervalService(TimingConfig timingConfig,
                               RuntimeSettingsRepository runtimeSettingsRepository,
                               JsonlEventLogger eventLogger,
                               RuntimeSettings initialSettings) {
        this.timingConfig = Objects.requireNonNull(timingConfig, "timingConfig");
        this.runtimeSettingsRepository = Objects.requireNonNull(runtimeSettingsRepository, "runtimeSettingsRepository");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger");
        this.currentSettings = new AtomicReference<>(Objects.requireNonNull(initialSettings, "initialSettings"));
    }

    public RuntimeSettings settings() {
        return currentSettings.get();
    }

    public Duration currentInterval() {
        return settings().syncInterval();
    }

    public RuntimeSettings updateInterval(Duration newInterval) {
        eventLogger.log(JsonlEventType.SYNC_INTERVAL_CHANGE_REQUESTED, Map.of("requestedInterval", newInterval.toString()));
        validate(newInterval);
        RuntimeSettings updated = currentSettings.updateAndGet(existing -> existing.withSyncInterval(newInterval));
        runtimeSettingsRepository.save(updated);
        eventLogger.log(JsonlEventType.SYNC_INTERVAL_CHANGED, Map.of(
                "syncInterval", updated.syncInterval().toString(),
                "configurationVersion", updated.configurationVersion()));
        return updated;
    }

    public void validate(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            eventLogger.log(JsonlEventType.SYNC_INTERVAL_CHANGE_REJECTED, Map.of("reason", "Interval must be positive"));
            throw new IllegalArgumentException("Interval must be positive");
        }
        if (interval.compareTo(timingConfig.minSyncInterval()) < 0) {
            eventLogger.log(JsonlEventType.SYNC_INTERVAL_CHANGE_REJECTED, Map.of("reason", "Interval is below minimum"));
            throw new IllegalArgumentException("Interval is below minimum");
        }
        if (interval.compareTo(timingConfig.maxSyncInterval()) > 0) {
            eventLogger.log(JsonlEventType.SYNC_INTERVAL_CHANGE_REJECTED, Map.of("reason", "Interval is above maximum"));
            throw new IllegalArgumentException("Interval is above maximum");
        }
    }
}