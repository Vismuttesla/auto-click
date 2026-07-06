package com.click.precisiontrigger.auth;

import java.time.Instant;

public record JwtMetadata(Instant expiresAt, Instant notBefore, Instant issuedAt, String subject) {
}