package com.click.precisiontrigger.planning;

import com.click.precisiontrigger.clock.FakeNanoClock;
import com.click.precisiontrigger.latency.LatencySample;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ServerClockModelTest {
    @Test
    void advancesServerTimeUsingMonotonicClock() {
        FakeNanoClock nanoClock = new FakeNanoClock(1_000L);
        ServerClockModel model = new ServerClockModel(nanoClock);
        model.updateFromSample(new LatencySample(
                1L,
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:01Z"),
                500L,
                1_000L,
                Duration.ofMillis(10),
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ZERO,
                "strategy",
                0.9,
                0.8,
                true,
                null,
                Duration.ofSeconds(60),
                1L),
                Instant.parse("2026-07-06T00:00:00Z"));

        nanoClock.advance(Duration.ofMillis(5));

        assertThat(model.now()).isEqualTo(Instant.parse("2026-07-06T00:00:01.005Z"));
    }

    @Test
    void marksExistingClockAsPotentiallyStale() {
        FakeNanoClock nanoClock = new FakeNanoClock(1_000L);
        ServerClockModel model = new ServerClockModel(nanoClock);
        model.updateFromSample(new LatencySample(
                1L,
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:01Z"),
                500L,
                1_000L,
                Duration.ofMillis(10),
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ZERO,
                "strategy",
                0.9,
                0.8,
                true,
                null,
                Duration.ofSeconds(60),
                1L),
                Instant.parse("2026-07-06T00:00:00Z"));

        model.markPotentiallyStale(Instant.parse("2026-07-06T00:02:00Z"));

        assertThat(model.snapshot()).isNotNull();
        assertThat(model.snapshot().confidence()).isZero();
        assertThat(model.snapshot().createdAt()).isEqualTo(Instant.parse("2026-07-06T00:02:00Z"));
        assertThat(model.snapshot().version()).isEqualTo(2L);
    }
}
