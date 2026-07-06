package com.abbos.precisiontrigger.auth;

import java.time.Instant;

public record AuthTokenSnapshot(
        boolean present,
        Instant updatedAt,
        long version,
        TokenSource source,
        Instant jwtExpiresAt,
        Instant jwtNotBefore,
        String jwtSubject,
        AuthTokenState state) {
}