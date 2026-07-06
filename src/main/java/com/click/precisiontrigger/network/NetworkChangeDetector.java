package com.click.precisiontrigger.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class NetworkChangeDetector {
    public Optional<Map<String, String>> detectChange(NetworkEnvironmentSnapshot previous, NetworkEnvironmentSnapshot current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (previous.equals(current)) {
            return Optional.empty();
        }
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("previousInterfaces", sortedValues(previous.interfaceNames()));
        attributes.put("currentInterfaces", sortedValues(current.interfaceNames()));
        attributes.put("previousAddresses", sortedValues(previous.localAddresses()));
        attributes.put("currentAddresses", sortedValues(current.localAddresses()));
        return Optional.of(attributes);
    }

    private static String sortedValues(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }
}
