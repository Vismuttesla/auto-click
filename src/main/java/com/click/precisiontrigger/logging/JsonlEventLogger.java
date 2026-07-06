package com.click.precisiontrigger.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class JsonlEventLogger implements Closeable {
    private final StructuredLogService logWriter;
    private final Clock clock;

    public JsonlEventLogger(Path directory) {
        this(directory, 10_000);
    }

    public JsonlEventLogger(Path directory, int asyncQueueCapacity) {
        this(
                new AsyncJsonlLogWriter(
                        Objects.requireNonNull(directory, "directory"),
                        new DailyUtcLogRotationPolicy(),
                        new LogEventSerializer(new ObjectMapper().registerModule(new JavaTimeModule())),
                        asyncQueueCapacity,
                        Duration.ofMillis(100)),
                Clock.systemUTC());
    }

    JsonlEventLogger(StructuredLogService logWriter, Clock clock) {
        this.logWriter = Objects.requireNonNull(logWriter, "logWriter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void log(JsonlEventType type, Map<String, Object> metadata) {
        Objects.requireNonNull(type, "type");
        logWriter.log(LogEvent.create(
                LogEventType.valueOf(type.name()),
                priorityOf(type),
                Instant.now(clock),
                metadata == null ? Map.of() : metadata));
    }

    @Override
    public void close() {
        logWriter.flush(Duration.ofSeconds(2));
        logWriter.close();
    }

    private static LogPriority priorityOf(JsonlEventType type) {
        return switch (type) {
            case ACTION_FIRED,
                 ACTION_ACKNOWLEDGED,
                 ACTION_REJECTED,
                 ACTION_FAILED,
                 ACTION_AMBIGUOUS -> LogPriority.CRITICAL;
            default -> LogPriority.NORMAL;
        };
    }
}
