package com.click.precisiontrigger.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LogEventSerializer {
    private final ObjectMapper objectMapper;

    public LogEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String serialize(LogEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            return objectMapper.writeValueAsString(normalizeMap(event.toOrderedDocument()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize log event " + event.eventType(), ex);
        }
    }

    private Map<String, Object> normalizeMap(Map<String, ?> values) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Duration duration) {
            return duration.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> nestedMap) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            nestedMap.forEach((key, nestedValue) -> normalized.put(String.valueOf(key), normalizeValue(nestedValue)));
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(normalizeValue(item)));
            return normalized;
        }
        return value;
    }
}
