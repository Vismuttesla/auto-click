package com.click.precisiontrigger.latency;

import java.time.Duration;
import java.time.Instant;

public record LatencySample(
        long sequence,
        Instant localCollectedAt,
        Instant serverTimestamp,
        Instant estimatedServerTimeAtReceive,
        long localSendNano,
        long localReceiveNano,
        Duration s1,
        Duration s2,
        Duration rtt,
        Duration jitter,
        String estimationStrategy,
        double qualityScore,
        double confidence,
        boolean accepted,
        String rejectionReason,
        Duration activeSyncInterval,
        long configurationVersion) {
}