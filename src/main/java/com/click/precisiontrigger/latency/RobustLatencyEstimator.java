package com.click.precisiontrigger.latency;

import com.click.precisiontrigger.time.TimeMeasurement;
import com.click.precisiontrigger.time.TimestampSemantics;

import java.time.Duration;

public final class RobustLatencyEstimator implements LatencyEstimator {
    private final S1S2EstimationStrategy estimationStrategy;
    private final OutlierFilter outlierFilter;
    private final JitterEstimator jitterEstimator;
    private final SampleQualityScorer qualityScorer;
    private final TimestampSemantics timestampSemantics;

    public RobustLatencyEstimator(S1S2EstimationStrategy estimationStrategy,
                                  OutlierFilter outlierFilter,
                                  JitterEstimator jitterEstimator,
                                  SampleQualityScorer qualityScorer,
                                  TimestampSemantics timestampSemantics) {
        this.estimationStrategy = estimationStrategy;
        this.outlierFilter = outlierFilter;
        this.jitterEstimator = jitterEstimator;
        this.qualityScorer = qualityScorer;
        this.timestampSemantics = timestampSemantics;
    }

    @Override
    public LatencySample estimate(TimeMeasurement measurement, SampleWindowSnapshot history, Duration activeSyncInterval) {
        LatencyEstimate estimate = estimationStrategy.estimate(measurement, history);
        Duration jitter = jitterEstimator.estimate(estimate.rtt(), history);
        boolean outlier = outlierFilter.isOutlier(estimate.rtt(), history);
        boolean accepted = estimate.rtt().toNanos() >= 0 && !outlier;
        double quality = qualityScorer.score(estimate.rtt(), jitter, outlier, estimate.assumptionBased(), timestampSemantics);
        double confidence = accepted ? Math.min(1.0, estimate.confidence() * quality) : 0.0;
        Duration receiveCompensation = timestampSemantics == TimestampSemantics.UNKNOWN ? estimate.s2() : Duration.ZERO;
        return new LatencySample(
                measurement.requestSequence(),
                measurement.localCollectedAt(),
                measurement.serverTimestamp(),
                measurement.serverTimestamp().plus(receiveCompensation),
                measurement.localSendNano(),
                measurement.localReceiveNano(),
                estimate.s1(),
                estimate.s2(),
                estimate.rtt(),
                jitter,
                estimate.estimationStrategy(),
                quality,
                confidence,
                accepted,
                accepted ? null : "RTT sample rejected by outlier policy",
                activeSyncInterval,
                measurement.configurationVersion());
    }
}