package com.abbos.precisiontrigger.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncJsonlLogWriterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void appendsJsonlAcrossWriterRestart() throws Exception {
        Path logDir = tempDir.resolve("logs");
        LogRotationPolicy rotationPolicy = new DailyUtcLogRotationPolicy();
        LogEventSerializer serializer = new LogEventSerializer(objectMapper);

        try (AsyncJsonlLogWriter writer = new AsyncJsonlLogWriter(logDir, rotationPolicy, serializer, 16, Duration.ofMillis(50))) {
            writer.log(LogEvent.create(
                    LogEventType.APPLICATION_STARTED,
                    LogPriority.NORMAL,
                    Instant.parse("2026-07-06T01:00:00Z"),
                    Map.of("configurationVersion", 1L)
            ));
            writer.flush(Duration.ofSeconds(2));
        }

        try (AsyncJsonlLogWriter writer = new AsyncJsonlLogWriter(logDir, rotationPolicy, serializer, 16, Duration.ofMillis(50))) {
            writer.log(LogEvent.create(
                    LogEventType.AUTH_TOKEN_APPLIED,
                    LogPriority.NORMAL,
                    Instant.parse("2026-07-06T01:05:00Z"),
                    AuthEventMetadata.tokenApplied(2L, "APPLICATION_UI", Instant.parse("2026-07-06T02:05:00Z")).toLogMetadata()
            ));
            writer.flush(Duration.ofSeconds(2));
        }

        Path logFile = logDir.resolve("precision-trigger-2026-07-06.jsonl");
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);

        assertThat(lines).hasSize(2);
        JsonNode first = objectMapper.readTree(lines.get(0));
        JsonNode second = objectMapper.readTree(lines.get(1));
        assertThat(first.get("eventType").textValue()).isEqualTo("APPLICATION_STARTED");
        assertThat(second.get("eventType").textValue()).isEqualTo("AUTH_TOKEN_APPLIED");
        assertThat(second.has("rawToken")).isFalse();
        assertThat(second.get("tokenValueLogged").booleanValue()).isFalse();
    }
}
