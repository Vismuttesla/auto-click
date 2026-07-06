package com.abbos.precisiontrigger.logging;

import java.time.Instant;
import java.util.Map;

public record JsonlEvent(Instant timestamp, JsonlEventType eventType, Map<String, Object> metadata) {
}