package com.click.precisiontrigger.logsearch;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record LogSearchQuery(
        Instant from,
        Instant to,
        String eventType,
        Long sequence,
        Long minS1Nanos,
        Long maxS1Nanos,
        Long minS2Nanos,
        Long maxS2Nanos,
        Long minRttNanos,
        Long maxRttNanos,
        Double minConfidence,
        Boolean accepted,
        String estimationStrategy,
        String freeText,
        LogSearchOrder order,
        int page,
        int pageSize) {

    public LogSearchQuery {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        order = order == null ? LogSearchOrder.NEWEST_FIRST : order;
        page = Math.max(0, page);
        pageSize = Math.max(1, Math.min(200, pageSize <= 0 ? 50 : pageSize));
    }

    public static LogSearchQuery defaults() {
        return new LogSearchQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null, LogSearchOrder.NEWEST_FIRST, 0, 50);
    }

    public static LogSearchQuery unfiltered() {
        return defaults();
    }

    public int offset() {
        return page * pageSize;
    }

    public int limit() {
        return pageSize;
    }

    public boolean matches(LogEventSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (from != null && snapshot.timestamp().isBefore(from)) {
            return false;
        }
        if (to != null && snapshot.timestamp().isAfter(to)) {
            return false;
        }
        if (eventType != null && !eventType.isBlank() && !snapshot.eventType().name().equalsIgnoreCase(eventType)) {
            return false;
        }
        if (sequence != null && !sequence.equals(snapshot.sequence())) {
            return false;
        }
        if (minConfidence != null && (snapshot.confidence() == null || snapshot.confidence() < minConfidence)) {
            return false;
        }
        if (accepted != null && !accepted.equals(snapshot.accepted())) {
            return false;
        }
        if (freeText != null && !freeText.isBlank()) {
            String needle = freeText.toLowerCase(Locale.ROOT);
            String metadataText = snapshot.metadata().toString().toLowerCase(Locale.ROOT);
            if (!metadataText.contains(needle)
                    && !snapshot.eventType().name().toLowerCase(Locale.ROOT).contains(needle)
                    && !snapshot.eventId().toLowerCase(Locale.ROOT).contains(needle)) {
                return false;
            }
        }
        return true;
    }
}
