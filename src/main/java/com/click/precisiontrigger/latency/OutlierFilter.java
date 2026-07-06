package com.click.precisiontrigger.latency;

import java.time.Duration;

public interface OutlierFilter {
    boolean isOutlier(Duration candidateRtt, SampleWindowSnapshot history);
}