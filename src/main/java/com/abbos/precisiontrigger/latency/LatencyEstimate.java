package com.abbos.precisiontrigger.latency;

import java.time.Duration;

public record LatencyEstimate(
        Duration s1,
        Duration s2,
        Duration rtt,
        String estimationStrategy,
        double confidence,
        boolean assumptionBased) {
}