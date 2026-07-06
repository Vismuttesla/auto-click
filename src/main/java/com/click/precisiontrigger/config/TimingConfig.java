package com.click.precisiontrigger.config;

import java.time.Duration;

public record TimingConfig(
        Duration syncInterval,
        Duration minSyncInterval,
        Duration maxSyncInterval,
        int sampleWindowSize,
        Duration maxClockAge,
        double minimumConfidence,
        Duration finalFreezeWindow,
        Duration coarseThreshold,
        Duration spinThreshold,
        Duration defaultActionOverhead) {
}