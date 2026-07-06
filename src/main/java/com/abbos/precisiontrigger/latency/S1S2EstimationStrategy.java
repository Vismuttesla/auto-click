package com.abbos.precisiontrigger.latency;

import com.abbos.precisiontrigger.time.TimeMeasurement;

public interface S1S2EstimationStrategy {
    LatencyEstimate estimate(TimeMeasurement measurement, SampleWindowSnapshot history);

    String name();
}