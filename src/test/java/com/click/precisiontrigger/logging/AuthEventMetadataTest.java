package com.click.precisiontrigger.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEventMetadataTest {
    @Test
    void exposesOnlySafeFieldsForAuthAuditLogging() {
        AuthEventMetadata metadata = AuthEventMetadata.tokenApplied(
                7L,
                "APPLICATION_UI",
                Instant.parse("2026-07-05T15:30:00Z")
        );

        Map<String, Object> document = metadata.toLogMetadata();

        assertThat(document)
                .containsEntry("tokenVersion", 7L)
                .containsEntry("source", "APPLICATION_UI")
                .containsEntry("tokenValueLogged", false);
        assertThat(document).containsKey("jwtExpiresAt");
        assertThat(document.keySet()).doesNotContain("rawToken", "authorizationHeader", "jwtSignature", "token");
    }
}
