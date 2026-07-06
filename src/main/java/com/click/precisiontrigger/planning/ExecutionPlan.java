package com.click.precisiontrigger.planning;

import java.time.Duration;
import java.time.Instant;

public record ExecutionPlan(
        Instant targetServerTime,
        Instant desiredFireServerTime,
        long localDeadlineNano,
        Duration selectedS1,
        Duration selectedS2,
        Duration executionOverhead,
        Duration jitter,
        double confidence,
        long sourceSampleSequence,
        long clockVersion,
        long planVersion,
        long configurationVersion,
        Instant createdAt) {
}