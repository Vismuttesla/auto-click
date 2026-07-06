package com.click.precisiontrigger.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AuthEventMetadata(
        long tokenVersion,
        String source,
        Instant jwtExpiresAt,
        String failureCode,
        String safeMessage,
        boolean tokenValueLogged
) {
    public AuthEventMetadata {
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("tokenVersion must be non-negative");
        }
        Objects.requireNonNull(source, "source");
    }

    public static AuthEventMetadata tokenApplied(long tokenVersion, String source, Instant jwtExpiresAt) {
        return new AuthEventMetadata(tokenVersion, source, jwtExpiresAt, null, null, false);
    }

    public static AuthEventMetadata tokenCleared(long tokenVersion, String source) {
        return new AuthEventMetadata(tokenVersion, source, null, null, null, false);
    }

    public static AuthEventMetadata authFailure(long tokenVersion, String source, String failureCode, String safeMessage) {
        return new AuthEventMetadata(tokenVersion, source, null, failureCode, safeMessage, false);
    }

    public Map<String, Object> toLogMetadata() {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tokenVersion", tokenVersion);
        metadata.put("source", source);
        if (jwtExpiresAt != null) {
            metadata.put("jwtExpiresAt", jwtExpiresAt);
        }
        if (failureCode != null && !failureCode.isBlank()) {
            metadata.put("failureCode", failureCode);
        }
        if (safeMessage != null && !safeMessage.isBlank()) {
            metadata.put("safeMessage", safeMessage);
        }
        metadata.put("tokenValueLogged", tokenValueLogged);
        return metadata;
    }
}
