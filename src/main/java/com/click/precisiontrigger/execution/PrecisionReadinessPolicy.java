package com.click.precisiontrigger.execution;

import com.click.precisiontrigger.config.TimingConfig;
import com.click.precisiontrigger.planning.ServerClockSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class PrecisionReadinessPolicy {
    private final TimingConfig timingConfig;

    public PrecisionReadinessPolicy(TimingConfig timingConfig) {
        this.timingConfig = Objects.requireNonNull(timingConfig, "timingConfig");
    }

    public PrecisionReadinessSnapshot evaluate(ServerClockSnapshot snapshot, Instant now) {
        if (snapshot == null) {
            return new PrecisionReadinessSnapshot(false, PrecisionReadinessStatus.NO_CLOCK, "No server clock snapshot is available", null, 0.0);
        }
        Duration age = Duration.between(snapshot.createdAt(), now);
        if (age.compareTo(timingConfig.maxClockAge()) > 0) {
            return new PrecisionReadinessSnapshot(false, PrecisionReadinessStatus.STALE_CLOCK, "Clock is older than the allowed maximum", age, snapshot.confidence());
        }
        if (snapshot.confidence() < timingConfig.minimumConfidence()) {
            return new PrecisionReadinessSnapshot(false, PrecisionReadinessStatus.LOW_CONFIDENCE, "Clock confidence is below the required threshold", age, snapshot.confidence());
        }
        return new PrecisionReadinessSnapshot(true, PrecisionReadinessStatus.READY, "Ready", age, snapshot.confidence());
    }
}