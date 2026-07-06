package com.abbos.precisiontrigger.auth;

import java.time.Duration;
import java.time.Instant;

public record AuthStatusSnapshot(
        AuthTokenState state,
        boolean tokenPresent,
        long tokenVersion,
        Instant updatedAt,
        Instant jwtExpiresAt,
        Duration remainingLifetime,
        Instant lastAuthTestAt,
        String lastFailureCode,
        String safeLastFailureMessage) {
}