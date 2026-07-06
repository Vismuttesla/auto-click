package com.click.precisiontrigger.planning;

import com.click.precisiontrigger.clock.FakeNanoClock;
import com.click.precisiontrigger.latency.LatencySample;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPlanCalculatorTest {
    @Test
    void calculatesDesiredFireTimeAndLocalDeadline() {
        FakeNanoClock nanoClock = new FakeNanoClock(1_000_000L);
        ServerClockModel serverClockModel = new ServerClockModel(nanoClock);
        serverClockModel.updateFromSample(new LatencySample(
                7L,
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:10Z"),
                10L,
                20L,
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(2),
                "strategy",
                0.9,
                0.8,
                true,
                null,
                Duration.ofSeconds(60),
                3L),
                Instant.parse("2026-07-06T00:00:00Z"));
        ExecutionPlanCalculator calculator = new ExecutionPlanCalculator(serverClockModel, new FixedActionOverheadEstimator(Duration.ofMillis(50)), nanoClock);

        ExecutionPlan plan = calculator.calculate(
                Instant.parse("2026-07-06T00:00:12Z"),
                new LatencySample(
                        7L,
                        Instant.parse("2026-07-06T00:00:00Z"),
                        Instant.parse("2026-07-06T00:00:00Z"),
                        Instant.parse("2026-07-06T00:00:10Z"),
                        10L,
                        20L,
                        Duration.ofMillis(100),
                        Duration.ofMillis(100),
                        Duration.ofMillis(200),
                        Duration.ofMillis(2),
                        "strategy",
                        0.9,
                        0.8,
                        true,
                        null,
                        Duration.ofSeconds(60),
                        3L),
                3L,
                Instant.parse("2026-07-06T00:00:00Z"));

        assertThat(plan.desiredFireServerTime()).isEqualTo(Instant.parse("2026-07-06T00:00:11.850Z"));
        assertThat(plan.localDeadlineNano()).isGreaterThan(1_000_000L);
        assertThat(plan.planVersion()).isEqualTo(1L);
    }
}