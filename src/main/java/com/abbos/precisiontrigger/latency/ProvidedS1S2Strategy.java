package com.abbos.precisiontrigger.latency;

import com.abbos.precisiontrigger.time.TimeMeasurement;

public final class ProvidedS1S2Strategy implements S1S2EstimationStrategy {
    @Override
    public LatencyEstimate estimate(TimeMeasurement measurement, SampleWindowSnapshot history) {
        throw new IllegalStateException("Real provider S1/S2 algorithm remains unresolved");
    }

    @Override
    public String name() {
        return "PROVIDED_S1S2";
    }
}