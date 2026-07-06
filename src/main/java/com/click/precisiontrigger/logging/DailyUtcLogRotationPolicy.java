package com.click.precisiontrigger.logging;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class DailyUtcLogRotationPolicy implements LogRotationPolicy {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Override
    public Path resolvePath(Path directory, Instant timestamp) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(timestamp, "timestamp");
        return directory.resolve("precision-trigger-" + FORMATTER.format(timestamp) + ".jsonl");
    }
}
