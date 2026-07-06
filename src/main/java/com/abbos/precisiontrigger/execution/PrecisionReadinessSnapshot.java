package com.abbos.precisiontrigger.execution;

import java.time.Duration;

public record PrecisionReadinessSnapshot(
        boolean ready,
        PrecisionReadinessStatus status,
        String reason,
        Duration clockAge,
        double confidence) {
}