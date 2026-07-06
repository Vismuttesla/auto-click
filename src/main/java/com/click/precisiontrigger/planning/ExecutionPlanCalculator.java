package com.click.precisiontrigger.planning;

import com.click.precisiontrigger.clock.NanoClock;
import com.click.precisiontrigger.latency.LatencySample;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ExecutionPlanCalculator {
    private final ServerClock serverClock;
    private final ActionOverheadEstimator actionOverheadEstimator;
    private final NanoClock nanoClock;
    private final AtomicLong planVersion = new AtomicLong();

    public ExecutionPlanCalculator(ServerClock serverClock,
                                   ActionOverheadEstimator actionOverheadEstimator,
                                   NanoClock nanoClock) {
        this.serverClock = Objects.requireNonNull(serverClock, "serverClock");
        this.actionOverheadEstimator = Objects.requireNonNull(actionOverheadEstimator, "actionOverheadEstimator");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public ExecutionPlan calculate(Instant targetServerTime, LatencySample sample, long configurationVersion, Instant createdAt) {
        ServerClockSnapshot snapshot = Objects.requireNonNull(serverClock.snapshot(), "Server clock snapshot must exist");
        Instant desiredFireServerTime = targetServerTime.minus(sample.s1()).minus(actionOverheadEstimator.estimateExecutionOverhead());
        Instant serverNow = Objects.requireNonNull(serverClock.now(), "Server clock current time must exist");
        Duration untilFire = Duration.between(serverNow, desiredFireServerTime);
        long localDeadlineNano = nanoClock.nanoTime() + Math.max(0L, untilFire.toNanos());
        return new ExecutionPlan(
                targetServerTime,
                desiredFireServerTime,
                localDeadlineNano,
                sample.s1(),
                sample.s2(),
                actionOverheadEstimator.estimateExecutionOverhead(),
                sample.jitter(),
                sample.confidence(),
                sample.sequence(),
                snapshot.version(),
                planVersion.incrementAndGet(),
                configurationVersion,
                createdAt);
    }
}