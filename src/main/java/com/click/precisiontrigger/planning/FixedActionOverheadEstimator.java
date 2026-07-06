package com.click.precisiontrigger.planning;

import java.time.Duration;
import java.util.Objects;

public final class FixedActionOverheadEstimator implements ActionOverheadEstimator {
    private final Duration duration;

    public FixedActionOverheadEstimator(Duration duration) {
        this.duration = Objects.requireNonNull(duration, "duration");
    }

    @Override
    public Duration estimateExecutionOverhead() {
        return duration;
    }
}