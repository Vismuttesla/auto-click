package com.click.precisiontrigger.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogEventSerializerTest {
    private final LogEventSerializer serializer = new LogEventSerializer(new ObjectMapper());

    @Test
    void serializesSingleLineJsonWithReadableCanonicalFields() {
        LogEvent event = new LogEvent(
                "evt-1",
                LogEventType.TIME_SYNC_SAMPLE,
                LogPriority.NORMAL,
                Instant.parse("2026-07-06T00:00:00Z"),
                Map.of(
                        "sequence", 42L,
                        "accepted", true,
                        "activeSyncInterval", Duration.ofSeconds(30)
                )
        );

        String json = serializer.serialize(event);

        assertThat(json).doesNotContain("\n");
        assertThat(json).contains("\"eventId\":\"evt-1\"");
        assertThat(json).contains("\"eventType\":\"TIME_SYNC_SAMPLE\"");
        assertThat(json).contains("\"priority\":\"NORMAL\"");
        assertThat(json).contains("\"timestamp\":\"2026-07-06T00:00:00Z\"");
        assertThat(json).contains("\"activeSyncInterval\":\"PT30S\"");
    }
}
