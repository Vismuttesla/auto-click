package com.abbos.precisiontrigger.latency;

import java.time.Duration;

public interface OutlierFilter {
    boolean isOutlier(Duration candidateRtt, SampleWindowSnapshot history);
}