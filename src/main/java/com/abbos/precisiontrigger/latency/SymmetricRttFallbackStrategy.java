package com.abbos.precisiontrigger.latency;

import com.abbos.precisiontrigger.time.TimeMeasurement;

import java.time.Duration;

public final class SymmetricRttFallbackStrategy implements S1S2EstimationStrategy {
    @Override
    public LatencyEstimate estimate(TimeMeasurement measurement, SampleWindowSnapshot history) {
        Duration rtt = Duration.ofNanos(Math.max(0L, measurement.localReceiveNano() - measurement.localSendNano()));
        Duration half = rtt.dividedBy(2L);
        return new LatencyEstimate(half, half, rtt, name(), 0.60, true);
    }

    @Override
    public String name() {
        return "SYMMETRIC_RTT_FALLBACK";
    }
}