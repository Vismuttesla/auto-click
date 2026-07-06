package com.click.precisiontrigger.logsearch;

import com.click.precisiontrigger.logging.LogEvent;
import com.click.precisiontrigger.logging.LogEventSerializer;
import com.click.precisiontrigger.logging.LogEventType;
import com.click.precisiontrigger.logging.LogPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlLogHistoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LogEventSerializer serializer = new LogEventSerializer(objectMapper);

    @TempDir
    Path tempDir;

    @Test
    void filtersByTimeTypeSequenceConfidenceAndAccepted() throws Exception {
        writeLogFile(tempDir.resolve("precision-trigger-2026-07-05.jsonl"), List.of(
                event("evt-1", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-05T12:00:00Z"), 8, 0.41, false),
                event("evt-2", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-05T12:05:00Z"), 9, 0.91, true),
                event("evt-3", LogEventType.AUTH_TOKEN_APPLIED, Instant.parse("2026-07-05T12:10:00Z"), 10, 0.95, true)
        ));

        JsonlLogHistory history = new JsonlLogHistory(tempDir, objectMapper);
        LogSearchQuery query = new LogSearchQuery(
                Instant.parse("2026-07-05T12:04:00Z"),
                Instant.parse("2026-07-05T12:06:00Z"),
                LogEventType.TIME_SYNC_SAMPLE.name(),
                9L,
                null,
                null,
                null,
                null,
                null,
                null,
                0.90,
                true,
                null,
                null,
                LogSearchOrder.NEWEST_FIRST,
                0,
                10
        );

        LogSearchPage page = history.search(query);

        assertThat(page.totalMatches()).isEqualTo(1);
        assertThat(page.events()).extracting(LogEventSnapshot::eventId).containsExactly("evt-2");
        assertThat(page.malformedLines()).isZero();
    }

    @Test
    void paginatesNewestFirstAcrossMultipleFiles() throws Exception {
        writeLogFile(tempDir.resolve("precision-trigger-2026-07-05.jsonl"), List.of(
                event("evt-1", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-05T12:00:00Z"), 1, 0.30, true),
                event("evt-2", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-05T12:01:00Z"), 2, 0.40, true)
        ));
        writeLogFile(tempDir.resolve("precision-trigger-2026-07-06.jsonl"), List.of(
                event("evt-3", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-06T12:00:00Z"), 3, 0.50, true),
                event("evt-4", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-06T12:01:00Z"), 4, 0.60, true)
        ));

        JsonlLogHistory history = new JsonlLogHistory(tempDir, objectMapper);

        LogSearchPage firstPage = history.search(new LogSearchQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null, LogSearchOrder.NEWEST_FIRST, 0, 2));
        LogSearchPage secondPage = history.search(new LogSearchQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null, LogSearchOrder.NEWEST_FIRST, 1, 2));

        assertThat(firstPage.totalMatches()).isEqualTo(4);
        assertThat(firstPage.events()).extracting(LogEventSnapshot::eventId).containsExactly("evt-4", "evt-3");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextOffset()).isEqualTo(2);

        assertThat(secondPage.events()).extracting(LogEventSnapshot::eventId).containsExactly("evt-2", "evt-1");
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void skipsMalformedLinesIncludingPartialFinalLine() throws Exception {
        Path logFile = tempDir.resolve("precision-trigger-2026-07-06.jsonl");
        Files.writeString(
                logFile,
                serializer.serialize(event("evt-1", LogEventType.TIME_SYNC_SAMPLE, Instant.parse("2026-07-06T00:00:00Z"), 1, 0.75, true))
                        + System.lineSeparator()
                        + "{bad json"
                        + System.lineSeparator()
                        + "{\"eventId\":\"partial\"",
                StandardCharsets.UTF_8
        );

        JsonlLogHistory history = new JsonlLogHistory(tempDir, objectMapper);
        LogSearchPage page = history.search(LogSearchQuery.unfiltered());

        assertThat(page.events()).extracting(LogEventSnapshot::eventId).containsExactly("evt-1");
        assertThat(page.totalMatches()).isEqualTo(1);
        assertThat(page.malformedLines()).isEqualTo(2);
    }

    @Test
    void returnsImmutableMetadataSnapshots() throws Exception {
        writeLogFile(tempDir.resolve("precision-trigger-2026-07-06.jsonl"), List.of(
                new LogEvent(
                        "evt-1",
                        LogEventType.TIME_SYNC_SAMPLE,
                        LogPriority.NORMAL,
                        Instant.parse("2026-07-06T00:00:00Z"),
                        Map.of(
                                "sequence", 1L,
                                "accepted", true,
                                "confidence", 0.80,
                                "nested", Map.of("value", "x"),
                                "items", List.of("a", "b")
                        )
                )
        ));

        JsonlLogHistory history = new JsonlLogHistory(tempDir, objectMapper);
        LogEventSnapshot snapshot = history.search(LogSearchQuery.unfiltered()).events().getFirst();

        assertThatThrownBy(() -> snapshot.metadata().put("newKey", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<String, Object>) snapshot.metadata().get("nested")).put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<String>) snapshot.metadata().get("items")).add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void writeLogFile(Path path, List<LogEvent> events) throws IOException {
        Files.write(
                path,
                events.stream()
                        .map(serializer::serialize)
                        .toList(),
                StandardCharsets.UTF_8
        );
    }

    private LogEvent event(String eventId, LogEventType eventType, Instant timestamp, long sequence, double confidence, boolean accepted) {
        return new LogEvent(
                eventId,
                eventType,
                LogPriority.NORMAL,
                timestamp,
                Map.of(
                        "sequence", sequence,
                        "confidence", confidence,
                        "accepted", accepted
                )
        );
    }
}
