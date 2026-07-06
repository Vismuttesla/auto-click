package com.abbos.precisiontrigger.sync;

import com.abbos.precisiontrigger.client.ServerTimeClient;
import com.abbos.precisiontrigger.client.ServerTimeClientResult;
import com.abbos.precisiontrigger.client.ServerTimeFailureCode;
import com.abbos.precisiontrigger.clock.FakeNanoClock;
import com.abbos.precisiontrigger.latency.JitterEstimator;
import com.abbos.precisiontrigger.latency.MadOutlierFilter;
import com.abbos.precisiontrigger.latency.RobustLatencyEstimator;
import com.abbos.precisiontrigger.latency.SampleQualityScorer;
import com.abbos.precisiontrigger.latency.SampleWindow;
import com.abbos.precisiontrigger.latency.SymmetricRttFallbackStrategy;
import com.abbos.precisiontrigger.logging.JsonlEventLogger;
import com.abbos.precisiontrigger.planning.ServerClockModel;
import com.abbos.precisiontrigger.time.TimeMeasurement;
import com.abbos.precisiontrigger.time.TimestampSemantics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class SyncCoordinatorTest {
    @TempDir
    Path tempDir;

    @Test
    void skipsOverlappingSyncAttempt() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ServerTimeClient client = (sequence, configVersion) -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ServerTimeClientResult.failure(ServerTimeFailureCode.NETWORK_ERROR, "blocked");
        };
        SyncCoordinator coordinator = new SyncCoordinator(
                client,
                new RobustLatencyEstimator(new SymmetricRttFallbackStrategy(), new MadOutlierFilter(), new JitterEstimator(), new SampleQualityScorer(), TimestampSemantics.UNKNOWN),
                new SampleWindow(10),
                new ServerClockModel(new FakeNanoClock()),
                new JsonlEventLogger(tempDir));

        var executor = Executors.newFixedThreadPool(2);
        Future<SyncStatus> first = executor.submit(() -> coordinator.runSync(SyncTrigger.MANUAL, Duration.ofSeconds(60), 1L, Instant.now()));
        started.await();
        SyncStatus second = coordinator.runSync(SyncTrigger.PERIODIC, Duration.ofSeconds(60), 1L, Instant.now());
        release.countDown();

        assertThat(second).isEqualTo(SyncStatus.SKIPPED_ALREADY_RUNNING);
        assertThat(first.get()).isEqualTo(SyncStatus.FAILED);
        executor.shutdownNow();
    }

    @Test
    void updatesClockOnSuccessfulSync() {
        ServerTimeClient client = (sequence, configVersion) -> ServerTimeClientResult.success(new TimeMeasurement(
                10L,
                30L,
                Instant.parse("2026-07-06T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                200,
                sequence,
                configVersion,
                new BigDecimal("1783169575987.4")));
        ServerClockModel clockModel = new ServerClockModel(new FakeNanoClock(30L));
        SyncCoordinator coordinator = new SyncCoordinator(
                client,
                new RobustLatencyEstimator(new SymmetricRttFallbackStrategy(), new MadOutlierFilter(), new JitterEstimator(), new SampleQualityScorer(), TimestampSemantics.UNKNOWN),
                new SampleWindow(10),
                clockModel,
                new JsonlEventLogger(tempDir));

        SyncStatus status = coordinator.runSync(SyncTrigger.MANUAL, Duration.ofSeconds(60), 1L, Instant.now());

        assertThat(status).isEqualTo(SyncStatus.SUCCESS);
        assertThat(clockModel.snapshot()).isNotNull();
        assertThat(clockModel.snapshot().sourceSampleSequence()).isEqualTo(1L);
    }
}