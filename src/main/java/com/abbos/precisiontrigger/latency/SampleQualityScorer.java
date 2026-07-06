package com.abbos.precisiontrigger.latency;

import com.abbos.precisiontrigger.time.TimestampSemantics;

import java.time.Duration;

public final class SampleQualityScorer {
    public double score(Duration rtt, Duration jitter, boolean outlier, boolean assumptionBased, TimestampSemantics semantics) {
        double score = 1.0;
        score -= Math.min(0.4, rtt.toMillis() / 1000.0);
        score -= Math.min(0.2, jitter.toMillis() / 500.0);
        if (outlier) {
            score -= 0.4;
        }
        if (assumptionBased) {
            score -= 0.15;
        }
        if (semantics == TimestampSemantics.UNKNOWN) {
            score -= 0.10;
        }
        return Math.max(0.0, Math.min(1.0, score));
    }
}