package com.click.precisiontrigger.clock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class FakeNanoClock implements NanoClock {
    private final AtomicLong currentNanoTime;

    public FakeNanoClock() {
        this(0L);
    }

    public FakeNanoClock(long initialNanoTime) {
        currentNanoTime = new AtomicLong(initialNanoTime);
    }

    @Override
    public long nanoTime() {
        return currentNanoTime.get();
    }

    public void advance(Duration duration) {
        currentNanoTime.addAndGet(duration.toNanos());
    }

    public void setNanoTime(long nanoTime) {
        currentNanoTime.set(nanoTime);
    }
}