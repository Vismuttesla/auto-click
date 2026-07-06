package com.abbos.precisiontrigger.latency;

import com.abbos.precisiontrigger.time.TimeMeasurement;

import java.time.Duration;

public interface LatencyEstimator {
    LatencySample estimate(TimeMeasurement measurement, SampleWindowSnapshot history, Duration activeSyncInterval);
}