package com.click.precisiontrigger.clock;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SystemWallClock implements WallClock {
    private final Clock clock;

    public SystemWallClock() {
        this(Clock.systemUTC());
    }

    public SystemWallClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}