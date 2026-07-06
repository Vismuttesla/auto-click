package com.abbos.precisiontrigger.planning;

import java.time.Duration;

public interface ActionOverheadEstimator {
    Duration estimateExecutionOverhead();
}