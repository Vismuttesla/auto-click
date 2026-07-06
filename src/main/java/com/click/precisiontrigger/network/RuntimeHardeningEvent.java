package com.click.precisiontrigger.network;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RuntimeHardeningEvent(
        RuntimeHardeningEventType type,
        Instant detectedAt,
        String source,
        Map<String, String> attributes) {

    public RuntimeHardeningEvent {
        type = Objects.requireNonNull(type, "type");
        detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        source = Objects.requireNonNull(source, "source");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
}
