package com.abbos.precisiontrigger.network;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkChangeDetectorTest {
    private final NetworkChangeDetector detector = new NetworkChangeDetector();

    @Test
    void returnsAttributesWhenInterfacesOrAddressesChange() {
        Optional<Map<String, String>> change = detector.detectChange(
                new NetworkEnvironmentSnapshot(Set.of("eth0"), Set.of("10.0.0.1")),
                new NetworkEnvironmentSnapshot(Set.of("eth0", "tun0"), Set.of("10.8.0.2")));

        assertThat(change).isPresent();
        assertThat(change.orElseThrow())
                .containsEntry("previousInterfaces", "eth0")
                .containsEntry("currentInterfaces", "eth0,tun0")
                .containsEntry("previousAddresses", "10.0.0.1")
                .containsEntry("currentAddresses", "10.8.0.2");
    }

    @Test
    void returnsEmptyWhenSnapshotDidNotChange() {
        NetworkEnvironmentSnapshot snapshot = new NetworkEnvironmentSnapshot(Set.of("eth0"), Set.of("10.0.0.1"));

        assertThat(detector.detectChange(snapshot, snapshot)).isEmpty();
    }
}
