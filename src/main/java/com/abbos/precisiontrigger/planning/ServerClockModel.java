package com.abbos.precisiontrigger.planning;

import com.abbos.precisiontrigger.clock.NanoClock;
import com.abbos.precisiontrigger.latency.LatencySample;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerClockModel implements ServerClock {
    private final NanoClock nanoClock;
    private final AtomicReference<ServerClockSnapshot> snapshotReference;

    public ServerClockModel(NanoClock nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.snapshotReference = new AtomicReference<>();
    }

    public void updateFromSample(LatencySample sample, Instant createdAt) {
        if (!sample.accepted()) {
            return;
        }
        snapshotReference.updateAndGet(current -> new ServerClockSnapshot(
                sample.estimatedServerTimeAtReceive(),
                sample.localReceiveNano(),
                sample.s1(),
                sample.s2(),
                sample.jitter(),
                sample.confidence(),
                sample.sequence(),
                current == null ? 1L : current.version() + 1L,
                createdAt));
    }

    public void markPotentiallyStale(Instant detectedAt) {
        snapshotReference.updateAndGet(current -> {
            if (current == null) {
                return null;
            }
            return new ServerClockSnapshot(
                    current.estimatedServerTimeAtAnchor(),
                    current.localMonotonicAnchorNano(),
                    current.estimatedS1(),
                    current.estimatedS2(),
                    current.jitter(),
                    0.0,
                    current.sourceSampleSequence(),
                    current.version() + 1L,
                    detectedAt);
        });
    }

    @Override
    public Instant now() {
        ServerClockSnapshot snapshot = snapshot();
        if (snapshot == null) {
            return null;
        }
        long elapsedNanos = Math.max(0L, nanoClock.nanoTime() - snapshot.localMonotonicAnchorNano());
        return snapshot.estimatedServerTimeAtAnchor().plusNanos(elapsedNanos);
    }

    @Override
    public ServerClockSnapshot snapshot() {
        return snapshotReference.get();
    }
}
