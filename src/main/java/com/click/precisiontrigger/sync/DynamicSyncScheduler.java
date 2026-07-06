package com.click.precisiontrigger.sync;

import com.click.precisiontrigger.clock.WallClock;
import com.click.precisiontrigger.config.RuntimeSettings;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DynamicSyncScheduler implements AutoCloseable {
    private final SyncIntervalService syncIntervalService;
    private final SyncCoordinator syncCoordinator;
    private final WallClock wallClock;
    private final ScheduledExecutorService executorService;
    private final AtomicReference<ScheduledFuture<?>> scheduledTask = new AtomicReference<>();

    public DynamicSyncScheduler(SyncIntervalService syncIntervalService,
                                SyncCoordinator syncCoordinator,
                                WallClock wallClock) {
        this.syncIntervalService = Objects.requireNonNull(syncIntervalService, "syncIntervalService");
        this.syncCoordinator = Objects.requireNonNull(syncCoordinator, "syncCoordinator");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dynamic-sync-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        reschedule(syncIntervalService.settings());
    }

    public RuntimeSettings applyNewInterval(java.time.Duration interval) {
        RuntimeSettings updated = syncIntervalService.updateInterval(interval);
        reschedule(updated);
        return updated;
    }

    public SyncStatus triggerManualSync() {
        RuntimeSettings settings = syncIntervalService.settings();
        return syncCoordinator.runSync(SyncTrigger.MANUAL, settings.syncInterval(), settings.configurationVersion(), wallClock.now());
    }

    public SyncStatus triggerAuthTest() {
        RuntimeSettings settings = syncIntervalService.settings();
        return syncCoordinator.runSync(SyncTrigger.AUTH_TEST, settings.syncInterval(), settings.configurationVersion(), wallClock.now());
    }

    private void reschedule(RuntimeSettings settings) {
        ScheduledFuture<?> previous = scheduledTask.getAndSet(null);
        if (previous != null) {
            previous.cancel(false);
        }
        syncCoordinator.updateNextPlanned(wallClock.now().plus(settings.syncInterval()));
        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(() -> {
            RuntimeSettings current = syncIntervalService.settings();
            syncCoordinator.runSync(SyncTrigger.PERIODIC, current.syncInterval(), current.configurationVersion(), wallClock.now());
            syncCoordinator.updateNextPlanned(wallClock.now().plus(current.syncInterval()));
        }, settings.syncInterval().toMillis(), settings.syncInterval().toMillis(), TimeUnit.MILLISECONDS);
        scheduledTask.set(future);
    }

    public Instant nextPlannedSync() {
        return syncCoordinator.status().nextPlanned();
    }

    @Override
    public void close() {
        ScheduledFuture<?> future = scheduledTask.get();
        if (future != null) {
            future.cancel(false);
        }
        executorService.shutdownNow();
    }
}