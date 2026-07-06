package com.click.precisiontrigger.latency;

import com.click.precisiontrigger.time.TimeMeasurement;

public interface S1S2EstimationStrategy {
    LatencyEstimate estimate(TimeMeasurement measurement, SampleWindowSnapshot history);

    String name();
}