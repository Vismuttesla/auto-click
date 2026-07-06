package com.click.precisiontrigger.latency;

import com.click.precisiontrigger.time.TimeMeasurement;

import java.time.Duration;

public final class HistoricalCalibratedStrategy implements S1S2EstimationStrategy {
    private final SymmetricRttFallbackStrategy fallback = new SymmetricRttFallbackStrategy();

    @Override
    public LatencyEstimate estimate(TimeMeasurement measurement, SampleWindowSnapshot history) {
        if (history.samples().isEmpty()) {
            return fallback.estimate(measurement, history);
        }
        Duration average = history.samples().stream()
                .filter(LatencySample::accepted)
                .map(LatencySample::rtt)
                .reduce(Duration.ZERO, Duration::plus)
                .dividedBy(Math.max(1, history.samples().size()));
        Duration half = average.dividedBy(2L);
        return new LatencyEstimate(half, half, average, name(), 0.72, true);
    }

    @Override
    public String name() {
        return "HISTORICAL_CALIBRATED";
    }
}