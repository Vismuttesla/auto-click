package com.abbos.precisiontrigger.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlEventLoggerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @TempDir
    Path tempDir;

    @Test
    void writesFlatSearchableJsonlEvents() throws Exception {
        Instant timestamp = Instant.parse("2026-07-06T00:00:00Z");
        try (JsonlEventLogger logger = new JsonlEventLogger(
                new AsyncJsonlLogWriter(tempDir, new DailyUtcLogRotationPolicy(), new LogEventSerializer(objectMapper), 16, Duration.ofMillis(50)),
                Clock.fixed(timestamp, ZoneOffset.UTC))) {
            logger.log(JsonlEventType.TIME_SYNC_SAMPLE, Map.of(
                    "sequence", 7L,
                    "s1Nanos", 1_000_000L,
                    "s2Nanos", 2_000_000L,
                    "confidence", 0.91,
                    "accepted", true));
        }

        Path logFile = tempDir.resolve("precision-trigger-2026-07-06.jsonl");
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(lines.getFirst());

        assertThat(lines).hasSize(1);
        assertThat(root.get("eventType").textValue()).isEqualTo("TIME_SYNC_SAMPLE");
        assertThat(root.get("priority").textValue()).isEqualTo("NORMAL");
        assertThat(root.get("sequence").longValue()).isEqualTo(7L);
        assertThat(root.get("s1Nanos").longValue()).isEqualTo(1_000_000L);
        assertThat(root.get("accepted").booleanValue()).isTrue();
        assertThat(root.has("metadata")).isFalse();
    }
}
