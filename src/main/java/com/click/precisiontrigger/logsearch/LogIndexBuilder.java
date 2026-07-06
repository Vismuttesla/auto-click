package com.click.precisiontrigger.logsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LogIndexBuilder {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public LogIndex build(Path directory) {
        List<LogIndexEntry> entries = new ArrayList<>();
        try {
            if (Files.notExists(directory)) {
                return new LogIndex(List.of());
            }
            List<Path> files = Files.list(directory)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (Path file : files) {
                readFile(file, entries);
            }
        } catch (IOException ex) {
            return new LogIndex(List.of());
        }
        return new LogIndex(List.copyOf(entries));
    }

    private void readFile(Path file, List<LogIndexEntry> entries) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(line);
                    Instant timestamp = root.hasNonNull("timestamp") ? Instant.parse(root.get("timestamp").asText()) : null;
                    entries.add(new LogIndexEntry(
                            file,
                            lineNumber,
                            timestamp,
                            text(root, "eventType"),
                            longValue(root, "sequence"),
                            longValue(root, "s1Nanos"),
                            longValue(root, "s2Nanos"),
                            longValue(root, "rttNanos"),
                            longValue(root, "jitterNanos"),
                            doubleValue(root, "confidence"),
                            booleanValue(root, "accepted"),
                            text(root, "estimationStrategy"),
                            line));
                } catch (Exception ignored) {
                    // tolerate malformed lines and partially written final lines
                }
            }
        } catch (IOException ignored) {
            // tolerate unreadable files
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Long longValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.canConvertToLong() ? node.longValue() : null;
    }

    private static Double doubleValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.doubleValue() : null;
    }

    private static Boolean booleanValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isBoolean() ? node.booleanValue() : null;
    }
}