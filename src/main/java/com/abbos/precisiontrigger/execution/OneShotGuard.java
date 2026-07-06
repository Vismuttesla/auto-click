package com.abbos.precisiontrigger.execution;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OneShotGuard {
    private final AtomicBoolean fired = new AtomicBoolean();

    public boolean tryFire() {
        return fired.compareAndSet(false, true);
    }

    public void reset() {
        fired.set(false);
    }
}