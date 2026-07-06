package com.click.precisiontrigger.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtMetadataInspectorTest {
    private final JwtMetadataInspector inspector = new JwtMetadataInspector(new ObjectMapper());

    @Test
    void extractsJwtClaimsWhenTokenLooksLikeJwt() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":1893456000,\"nbf\":1700000000,\"iat\":1699990000,\"sub\":\"abbos\"}".getBytes());
        String token = header + "." + payload + ".signature";

        var metadata = inspector.inspect(token);

        assertThat(metadata).isPresent();
        assertThat(metadata.orElseThrow().expiresAt()).isEqualTo(Instant.ofEpochSecond(1893456000));
        assertThat(metadata.orElseThrow().notBefore()).isEqualTo(Instant.ofEpochSecond(1700000000));
        assertThat(metadata.orElseThrow().issuedAt()).isEqualTo(Instant.ofEpochSecond(1699990000));
        assertThat(metadata.orElseThrow().subject()).isEqualTo("abbos");
    }

    @Test
    void returnsEmptyForOpaqueToken() {
        assertThat(inspector.inspect("opaque-token")).isEmpty();
    }

    @Test
    void returnsEmptyForMalformedJwtPayload() {
        assertThat(inspector.inspect("a.b!.c")).isEmpty();
    }
}