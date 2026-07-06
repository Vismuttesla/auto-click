package com.click.precisiontrigger.network;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record NetworkEnvironmentSnapshot(Set<String> interfaceNames, Set<String> localAddresses) {
    public NetworkEnvironmentSnapshot {
        interfaceNames = Set.copyOf(new TreeSet<>(Objects.requireNonNull(interfaceNames, "interfaceNames")));
        localAddresses = Set.copyOf(new TreeSet<>(Objects.requireNonNull(localAddresses, "localAddresses")));
    }
}
