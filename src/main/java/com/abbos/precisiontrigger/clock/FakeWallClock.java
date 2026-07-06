package com.abbos.precisiontrigger.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class FakeWallClock implements WallClock {
    private final AtomicReference<Instant> currentInstant;

    public FakeWallClock(Instant initialInstant) {
        currentInstant = new AtomicReference<>(initialInstant);
    }

    @Override
    public Instant now() {
        return currentInstant.get();
    }

    public void advance(Duration duration) {
        currentInstant.updateAndGet(current -> current.plus(duration));
    }

    public void set(Instant instant) {
        currentInstant.set(instant);
    }
}