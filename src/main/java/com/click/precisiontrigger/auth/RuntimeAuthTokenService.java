package com.click.precisiontrigger.auth;

import com.click.precisiontrigger.config.AuthConfig;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.logging.JsonlEventType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeAuthTokenService implements AuthTokenProvider {
    private final AtomicReference<InternalTokenHolder> holder;
    private final JwtMetadataInspector jwtMetadataInspector;
    private final JsonlEventLogger eventLogger;
    private final Duration expiringSoonThreshold;
    private final Duration unauthorizedBackoff;

    public RuntimeAuthTokenService(JwtMetadataInspector jwtMetadataInspector,
                                   JsonlEventLogger eventLogger,
                                   AuthConfig authConfig) {
        this.jwtMetadataInspector = Objects.requireNonNull(jwtMetadataInspector, "jwtMetadataInspector");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger");
        this.expiringSoonThreshold = Objects.requireNonNull(authConfig, "authConfig").expiringSoonThreshold();
        this.unauthorizedBackoff = authConfig.unauthorizedBackoff();
        holder = new AtomicReference<>(InternalTokenHolder.empty());
    }

    public AuthStatusSnapshot applyToken(String rawToken, Instant now) {
        String normalized = normalize(rawToken);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Bearer token must not be blank");
        }
        Optional<JwtMetadata> metadata = jwtMetadataInspector.inspect(normalized);
        InternalTokenHolder current = holder.get();
        AuthTokenState state = resolveState(metadata.orElse(null), now, false, false);
        long nextVersion = current.snapshot.version() + 1;
        InternalTokenHolder updated = InternalTokenHolder.of(
                normalized,
                new AuthTokenSnapshot(
                        true,
                        now,
                        nextVersion,
                        TokenSource.APPLICATION_UI,
                        metadata.map(JwtMetadata::expiresAt).orElse(null),
                        metadata.map(JwtMetadata::notBefore).orElse(null),
                        metadata.map(JwtMetadata::subject).orElse(null),
                        state),
                new AuthStatusSnapshot(
                        state,
                        true,
                        nextVersion,
                        now,
                        metadata.map(JwtMetadata::expiresAt).orElse(null),
                        remainingLifetime(metadata.orElse(null), now),
                        current.status.lastAuthTestAt(),
                        null,
                        null),
                current.lastRejectedAt());
        holder.set(updated);
        eventLogger.log(JsonlEventType.AUTH_TOKEN_APPLIED, Map.of(
                "tokenVersion", nextVersion,
                "state", state.name(),
                "jwtExpiresAt", String.valueOf(updated.snapshot.jwtExpiresAt()),
                "tokenValueLogged", false));
        return updated.status;
    }

    public AuthStatusSnapshot clearToken(Instant now) {
        InternalTokenHolder current = holder.get();
        long nextVersion = current.snapshot.version() + 1;
        InternalTokenHolder cleared = InternalTokenHolder.of(
                null,
                new AuthTokenSnapshot(false, now, nextVersion, TokenSource.NONE, null, null, null, AuthTokenState.NO_TOKEN),
                new AuthStatusSnapshot(AuthTokenState.NO_TOKEN, false, nextVersion, now, null, null, current.status.lastAuthTestAt(), null, null),
                current.lastRejectedAt());
        holder.set(cleared);
        eventLogger.log(JsonlEventType.AUTH_TOKEN_CLEARED, Map.of("tokenVersion", nextVersion, "tokenValueLogged", false));
        return cleared.status;
    }

    public void markAuthenticated(Instant now) {
        updateStatus(now, AuthTokenState.AUTHENTICATED, null, null, false, false, JsonlEventType.AUTH_TEST_SUCCEEDED);
    }

    public void markUnauthorized(Instant now, String safeMessage) {
        updateStatus(now, AuthTokenState.AUTH_REJECTED, "UNAUTHORIZED", safeMessage, true, false, JsonlEventType.AUTH_UNAUTHORIZED);
    }

    public void markForbidden(Instant now, String safeMessage) {
        updateStatus(now, AuthTokenState.FORBIDDEN, "FORBIDDEN", safeMessage, false, true, JsonlEventType.AUTH_FORBIDDEN);
    }

    public void markAuthFailure(Instant now, String code, String safeMessage) {
        updateStatus(now, snapshot().state(), code, safeMessage, false, false, JsonlEventType.AUTH_TEST_FAILED);
    }

    public boolean canIssueAuthenticatedRequest(Instant now) {
        InternalTokenHolder current = holder.get();
        if (!current.snapshot.present()) {
            return false;
        }
        if (current.status.state() != AuthTokenState.AUTH_REJECTED) {
            return true;
        }
        Instant lastRejectedAt = current.lastRejectedAt();
        return lastRejectedAt == null || now.isAfter(lastRejectedAt.plus(unauthorizedBackoff));
    }

    @Override
    public Optional<String> currentBearerToken() {
        InternalTokenHolder current = holder.get();
        return Optional.ofNullable(current.rawToken);
    }

    @Override
    public AuthTokenSnapshot snapshot() {
        return holder.get().snapshot;
    }

    @Override
    public AuthStatusSnapshot status() {
        return holder.get().status;
    }

    private void updateStatus(Instant now,
                              AuthTokenState state,
                              String failureCode,
                              String safeMessage,
                              boolean rejected,
                              boolean forbidden,
                              JsonlEventType eventType) {
        holder.updateAndGet(current -> {
            JwtMetadata metadata = new JwtMetadata(current.snapshot.jwtExpiresAt(), current.snapshot.jwtNotBefore(), null, current.snapshot.jwtSubject());
            AuthTokenState effectiveState = state == current.snapshot.state()
                    ? resolveState(metadata, now, rejected, forbidden)
                    : state;
            AuthTokenSnapshot snapshot = new AuthTokenSnapshot(
                    current.snapshot.present(),
                    now,
                    current.snapshot.version(),
                    current.snapshot.source(),
                    current.snapshot.jwtExpiresAt(),
                    current.snapshot.jwtNotBefore(),
                    current.snapshot.jwtSubject(),
                    effectiveState);
            AuthStatusSnapshot status = new AuthStatusSnapshot(
                    effectiveState,
                    current.snapshot.present(),
                    current.snapshot.version(),
                    now,
                    current.snapshot.jwtExpiresAt(),
                    remainingLifetime(metadata, now),
                    now,
                    failureCode,
                    safeMessage);
            Instant lastRejectedAt = rejected ? now : current.lastRejectedAt();
            eventLogger.log(eventType, Map.of(
                    "tokenVersion", snapshot.version(),
                    "state", effectiveState.name(),
                    "failureCode", String.valueOf(failureCode),
                    "tokenValueLogged", false));
            return InternalTokenHolder.of(current.rawToken, snapshot, status, lastRejectedAt);
        });
    }

    public static String normalize(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        String trimmed = rawToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private AuthTokenState resolveState(JwtMetadata metadata, Instant now, boolean rejected, boolean forbidden) {
        if (forbidden) {
            return AuthTokenState.FORBIDDEN;
        }
        if (rejected) {
            return AuthTokenState.AUTH_REJECTED;
        }
        if (metadata == null || metadata.expiresAt() == null) {
            return AuthTokenState.TOKEN_PRESENT;
        }
        if (!metadata.expiresAt().isAfter(now)) {
            return AuthTokenState.TOKEN_EXPIRED;
        }
        if (metadata.expiresAt().minus(expiringSoonThreshold).isBefore(now)) {
            return AuthTokenState.TOKEN_EXPIRING_SOON;
        }
        return AuthTokenState.TOKEN_PRESENT;
    }

    private static Duration remainingLifetime(JwtMetadata metadata, Instant now) {
        if (metadata == null || metadata.expiresAt() == null) {
            return null;
        }
        return Duration.between(now, metadata.expiresAt());
    }

    private record InternalTokenHolder(String rawToken,
                                       AuthTokenSnapshot snapshot,
                                       AuthStatusSnapshot status,
                                       Instant lastRejectedAt) {
        private static InternalTokenHolder empty() {
            Instant epoch = Instant.EPOCH;
            return new InternalTokenHolder(
                    null,
                    new AuthTokenSnapshot(false, epoch, 0L, TokenSource.NONE, null, null, null, AuthTokenState.NO_TOKEN),
                    new AuthStatusSnapshot(AuthTokenState.NO_TOKEN, false, 0L, epoch, null, null, null, null, null),
                    null);
        }

        private static InternalTokenHolder of(String rawToken,
                                              AuthTokenSnapshot snapshot,
                                              AuthStatusSnapshot status,
                                              Instant lastRejectedAt) {
            return new InternalTokenHolder(rawToken, snapshot, status, lastRejectedAt);
        }
    }
}