package com.click.precisiontrigger.latency;

import com.click.precisiontrigger.time.TimeMeasurement;

import java.time.Duration;

public interface LatencyEstimator {
    LatencySample estimate(TimeMeasurement measurement, SampleWindowSnapshot history, Duration activeSyncInterval);
}