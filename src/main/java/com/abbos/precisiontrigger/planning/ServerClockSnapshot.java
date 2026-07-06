package com.abbos.precisiontrigger.planning;

import java.time.Duration;
import java.time.Instant;

public record ServerClockSnapshot(
        Instant estimatedServerTimeAtAnchor,
        long localMonotonicAnchorNano,
        Duration estimatedS1,
        Duration estimatedS2,
        Duration jitter,
        double confidence,
        long sourceSampleSequence,
        long version,
        Instant createdAt) {
}