package com.click.precisiontrigger.logsearch;

import java.util.List;
import java.util.Objects;

public record LogSearchPage(
        List<LogEventSnapshot> events,
        long totalMatches,
        int offset,
        int limit,
        int malformedLines
) {
    public LogSearchPage {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be non-negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (malformedLines < 0) {
            throw new IllegalArgumentException("malformedLines must be non-negative");
        }
    }

    public boolean hasMore() {
        return offset + events.size() < totalMatches;
    }

    public int nextOffset() {
        return offset + events.size();
    }
}
