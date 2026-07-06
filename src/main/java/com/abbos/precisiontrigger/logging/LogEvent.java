package com.abbos.precisiontrigger.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LogEvent(
        String eventId,
        LogEventType eventType,
        LogPriority priority,
        Instant timestamp,
        Map<String, ?> metadata
) {
    public LogEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(timestamp, "timestamp");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LogEvent create(LogEventType eventType, LogPriority priority, Instant timestamp, Map<String, ?> metadata) {
        return new LogEvent(UUID.randomUUID().toString(), eventType, priority, timestamp, metadata);
    }

    public Map<String, Object> toOrderedDocument() {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("eventId", eventId);
        document.put("eventType", eventType.name());
        document.put("priority", priority.name());
        document.put("timestamp", timestamp);
        metadata.forEach(document::put);
        return document;
    }
}
