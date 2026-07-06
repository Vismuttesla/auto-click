package com.abbos.precisiontrigger.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

public final class JwtMetadataInspector {
    private final ObjectMapper objectMapper;

    public JwtMetadataInspector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JwtMetadata> inspect(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode root = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
            return Optional.of(new JwtMetadata(
                    asInstant(root.get("exp")),
                    asInstant(root.get("nbf")),
                    asInstant(root.get("iat")),
                    root.hasNonNull("sub") ? root.get("sub").asText() : null));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Instant asInstant(JsonNode node) {
        return node != null && node.canConvertToLong() ? Instant.ofEpochSecond(node.longValue()) : null;
    }
}