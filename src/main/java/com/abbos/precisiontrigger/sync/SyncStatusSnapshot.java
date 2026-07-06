package com.abbos.precisiontrigger.sync;

import java.time.Instant;

public record SyncStatusSnapshot(
        SyncStatus status,
        Instant lastStarted,
        Instant lastCompleted,
        Instant lastSuccessful,
        Instant nextPlanned,
        String lastFailure) {
}