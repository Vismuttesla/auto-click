package com.abbos.precisiontrigger.config;

import java.time.Duration;
import java.util.Objects;

public record RuntimeSettings(
        Duration syncInterval,
        String displayZoneId,
        long configurationVersion,
        TokenPersistencePolicy tokenPersistencePolicy
) {
    public static final RuntimeSettings DEFAULT = new RuntimeSettings(
            Duration.ofSeconds(60),
            "UTC",
            1L,
            TokenPersistencePolicy.MEMORY_ONLY
    );

    public RuntimeSettings {
        Objects.requireNonNull(syncInterval, "syncInterval");
        Objects.requireNonNull(displayZoneId, "displayZoneId");
        Objects.requireNonNull(tokenPersistencePolicy, "tokenPersistencePolicy");
        if (syncInterval.isZero() || syncInterval.isNegative()) {
            throw new IllegalArgumentException("syncInterval must be positive");
        }
        if (configurationVersion < 1) {
            throw new IllegalArgumentException("configurationVersion must be at least 1");
        }
    }

    public RuntimeSettings withSyncInterval(Duration interval) {
        return new RuntimeSettings(interval, displayZoneId, configurationVersion + 1, tokenPersistencePolicy);
    }
}
