package com.click.precisiontrigger.logsearch;

import java.nio.file.Path;
import java.time.Instant;

public record LogIndexEntry(
        Path file,
        long lineNumber,
        Instant timestamp,
        String eventType,
        Long sequence,
        Long s1Nanos,
        Long s2Nanos,
        Long rttNanos,
        Long jitterNanos,
        Double confidence,
        Boolean accepted,
        String estimationStrategy,
        String rawLine) {
}