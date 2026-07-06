package com.click.precisiontrigger.planning;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class TargetTimeService {
    private final AtomicReference<Instant> target = new AtomicReference<>();

    public void setTarget(Instant instant) {
        target.set(instant);
    }

    public Instant currentTarget() {
        return target.get();
    }

    public void clear() {
        target.set(null);
    }
}