package com.abbos.precisiontrigger.scheduler;

import com.abbos.precisiontrigger.clock.NanoClock;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

public final class HybridPrecisionScheduler implements PrecisionScheduler {
    private final NanoClock nanoClock;
    private final long coarseThresholdNanos;
    private final long spinThresholdNanos;
    private final ExecutorService executorService;
    private volatile Future<?> currentTask;

    public HybridPrecisionScheduler(NanoClock nanoClock, long coarseThresholdNanos, long spinThresholdNanos) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.coarseThresholdNanos = coarseThresholdNanos;
        this.spinThresholdNanos = spinThresholdNanos;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "precision-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void schedule(long deadlineNano, Runnable action) {
        cancel();
        currentTask = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long remaining = deadlineNano - nanoClock.nanoTime();
                if (remaining <= 0L) {
                    action.run();
                    return;
                }
                if (remaining > coarseThresholdNanos) {
                    LockSupport.parkNanos(Math.min(remaining - coarseThresholdNanos, coarseThresholdNanos));
                } else if (remaining > spinThresholdNanos) {
                    LockSupport.parkNanos(Math.min(remaining, spinThresholdNanos));
                } else {
                    Thread.onSpinWait();
                }
            }
        });
    }

    @Override
    public void cancel() {
        Future<?> task = currentTask;
        if (task != null) {
            task.cancel(true);
        }
    }
}