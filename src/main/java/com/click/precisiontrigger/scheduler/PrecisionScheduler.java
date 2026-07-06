package com.click.precisiontrigger.scheduler;

public interface PrecisionScheduler {
    void schedule(long deadlineNano, Runnable action);

    void cancel();
}