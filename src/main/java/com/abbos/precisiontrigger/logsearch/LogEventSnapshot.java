package com.abbos.precisiontrigger.logsearch;

import com.abbos.precisiontrigger.logging.LogEventType;
import com.abbos.precisiontrigger.logging.LogPriority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LogEventSnapshot(
        String eventId,
        LogEventType eventType,
        LogPriority priority,
        Instant timestamp,
        Long sequence,
        Double confidence,
        Boolean accepted,
        Map<String, Object> metadata
) {
    public LogEventSnapshot {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(timestamp, "timestamp");
        metadata = immutableCopy(metadata == null ? Map.of() : metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> immutableValue(entry.getValue())
                ));
    }

    @SuppressWarnings("unchecked")
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            return nestedMap.entrySet().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            entry -> String.valueOf(entry.getKey()),
                            entry -> immutableValue(entry.getValue())
                    ));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(LogEventSnapshot::immutableValue)
                    .toList();
        }
        return value;
    }
}
