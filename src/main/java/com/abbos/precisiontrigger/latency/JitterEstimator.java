package com.abbos.precisiontrigger.latency;

import java.time.Duration;
import java.util.List;

public final class JitterEstimator {
    public Duration estimate(Duration currentRtt, SampleWindowSnapshot history) {
        List<LatencySample> samples = history.samples();
        if (samples.isEmpty()) {
            return Duration.ZERO;
        }
        Duration previous = samples.get(samples.size() - 1).rtt();
        return currentRtt.minus(previous).abs();
    }
}