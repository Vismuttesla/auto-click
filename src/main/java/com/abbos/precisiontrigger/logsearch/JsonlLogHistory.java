package com.abbos.precisiontrigger.logsearch;

import com.abbos.precisiontrigger.logging.LogEventType;
import com.abbos.precisiontrigger.logging.LogPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class JsonlLogHistory implements LogHistory {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Comparator<LogEventSnapshot> NEWEST_FIRST = Comparator
            .comparing(LogEventSnapshot::timestamp)
            .reversed()
            .thenComparing(LogEventSnapshot::eventId);
    private static final Comparator<LogEventSnapshot> OLDEST_FIRST = Comparator
            .comparing(LogEventSnapshot::timestamp)
            .thenComparing(LogEventSnapshot::eventId);

    private final Path logDirectory;
    private final ObjectMapper objectMapper;

    public JsonlLogHistory(Path logDirectory) {
        this(logDirectory, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public JsonlLogHistory(Path logDirectory, ObjectMapper objectMapper) {
        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public LogSearchPage search(LogSearchQuery query) throws IOException {
        Objects.requireNonNull(query, "query");
        if (!Files.exists(logDirectory)) {
            return new LogSearchPage(List.of(), 0, query.offset(), query.limit(), 0);
        }

        SearchAccumulator accumulator = new SearchAccumulator(query);
        try (Stream<Path> pathStream = Files.list(logDirectory)) {
            List<Path> logFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path logFile : logFiles) {
                scanFile(logFile, accumulator);
            }
        }

        Comparator<LogEventSnapshot> comparator = query.order() == LogSearchOrder.OLDEST_FIRST ? OLDEST_FIRST : NEWEST_FIRST;
        List<LogEventSnapshot> page = accumulator.matches.stream()
                .sorted(comparator)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
        return new LogSearchPage(page, accumulator.matches.size(), query.offset(), query.limit(), accumulator.malformedLines);
    }

    private void scanFile(Path logFile, SearchAccumulator accumulator) throws IOException {
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    LogEventSnapshot snapshot = parseSnapshot(line);
                    if (accumulator.query.matches(snapshot)) {
                        accumulator.matches.add(snapshot);
                    }
                } catch (RuntimeException ex) {
                    accumulator.malformedLines++;
                }
            }
        }
    }

    private LogEventSnapshot parseSnapshot(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            String eventId = requiredText(root, "eventId");
            LogEventType eventType = LogEventType.valueOf(requiredText(root, "eventType"));
            LogPriority priority = LogPriority.valueOf(requiredText(root, "priority"));
            Instant timestamp = Instant.parse(requiredText(root, "timestamp"));
            Map<String, Object> metadata = extractMetadata(root);
            return new LogEventSnapshot(
                    eventId,
                    eventType,
                    priority,
                    timestamp,
                    asLong(metadata.get("sequence")),
                    asDouble(metadata.get("confidence")),
                    asBoolean(metadata.get("accepted")),
                    metadata
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed JSONL log line", ex);
        }
    }

    private Map<String, Object> extractMetadata(JsonNode root) throws JsonProcessingException {
        LinkedHashMap<String, Object> document = objectMapper.convertValue(root, MAP_TYPE);
        document.remove("eventId");
        document.remove("eventType");
        document.remove("priority");
        document.remove("timestamp");
        return document;
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Missing textual field " + fieldName);
        }
        return value.textValue();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            return Double.parseDouble(text);
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static final class SearchAccumulator {
        private final LogSearchQuery query;
        private final List<LogEventSnapshot> matches = new ArrayList<>();
        private int malformedLines;

        private SearchAccumulator(LogSearchQuery query) {
            this.query = query;
        }
    }
}
