package com.abbos.precisiontrigger.network;

import com.abbos.precisiontrigger.clock.WallClock;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class NetworkEnvironmentMonitor implements AutoCloseable {
    private final WallClock wallClock;
    private final RuntimeHardeningSignalSink signalSink;
    private final Duration pollInterval;
    private final NetworkChangeDetector changeDetector;
    private final SnapshotSupplier snapshotSupplier;
    private final ScheduledExecutorService executorService;
    private final AtomicReference<NetworkEnvironmentSnapshot> previousSnapshot = new AtomicReference<>();
    private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();

    public NetworkEnvironmentMonitor(WallClock wallClock,
                                     RuntimeHardeningSignalSink signalSink,
                                     Duration pollInterval,
                                     NetworkChangeDetector changeDetector,
                                     SnapshotSupplier snapshotSupplier) {
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
        this.signalSink = Objects.requireNonNull(signalSink, "signalSink");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.changeDetector = Objects.requireNonNull(changeDetector, "changeDetector");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "network-environment-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executorService.scheduleWithFixedDelay(this::pollSafely, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException ignored) {
            // monitoring is best-effort and must not break the application runtime
        }
    }

    private void poll() {
        Instant now = wallClock.now();
        Instant previousPoll = lastPollAt.getAndSet(now);
        if (previousPoll != null) {
            Duration observedGap = Duration.between(previousPoll, now);
            if (observedGap.compareTo(pollInterval.multipliedBy(3)) > 0) {
                signalSink.systemResumed(now, "network-monitor", observedGap, Map.of("pollInterval", pollInterval.toString()));
            }
        }

        NetworkEnvironmentSnapshot current = snapshotSupplier.snapshot();
        NetworkEnvironmentSnapshot previous = previousSnapshot.getAndSet(current);
        if (previous != null) {
            changeDetector.detectChange(previous, current)
                    .ifPresent(attributes -> signalSink.networkEnvironmentChanged(now, "network-monitor", attributes));
        }
    }

    public interface SnapshotSupplier {
        NetworkEnvironmentSnapshot snapshot();
    }

    public static SnapshotSupplier systemSnapshotSupplier() {
        return () -> {
            try {
                Set<String> interfaces = new HashSet<>();
                Set<String> addresses = new HashSet<>();
                Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
                if (enumeration == null) {
                    return new NetworkEnvironmentSnapshot(Set.of(), Set.of());
                }
                for (NetworkInterface networkInterface : Collections.list(enumeration)) {
                    if (!networkInterface.isUp()) {
                        continue;
                    }
                    interfaces.add(networkInterface.getName());
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                        addresses.add(inetAddress.getHostAddress());
                    }
                }
                return new NetworkEnvironmentSnapshot(interfaces, addresses);
            } catch (SocketException ex) {
                throw new IllegalStateException("Failed to inspect network interfaces", ex);
            }
        };
    }
}
