package com.click.precisiontrigger.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class FileBackedRuntimeSettingsRepository implements RuntimeSettingsRepository {
    private final Path path;
    private final ObjectMapper objectMapper;

    public FileBackedRuntimeSettingsRepository(Path path, ObjectMapper objectMapper) {
        this.path = Objects.requireNonNull(path, "path");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public RuntimeSettings load() {
        if (!Files.exists(path)) {
            return RuntimeSettings.DEFAULT;
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            Duration syncInterval = readDuration(root, "syncInterval", RuntimeSettings.DEFAULT.syncInterval());
            String displayZoneId = readText(root, "displayZoneId", RuntimeSettings.DEFAULT.displayZoneId());
            long configurationVersion = readLong(root, "configurationVersion", RuntimeSettings.DEFAULT.configurationVersion());
            TokenPersistencePolicy policy = readPolicy(root, "tokenPersistencePolicy", RuntimeSettings.DEFAULT.tokenPersistencePolicy());
            return new RuntimeSettings(syncInterval, displayZoneId, configurationVersion, policy);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read runtime settings from " + path, ex);
        }
    }

    @Override
    public void save(RuntimeSettings settings) {
        Objects.requireNonNull(settings, "settings");

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("syncInterval", settings.syncInterval().toString());
        payload.put("displayZoneId", settings.displayZoneId());
        payload.put("configurationVersion", settings.configurationVersion());
        payload.put("tokenPersistencePolicy", settings.tokenPersistencePolicy().name());

        try {
            Files.createDirectories(path.getParent());
            Path tempFile = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), payload);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write runtime settings to " + path, ex);
        }
    }

    private static Duration readDuration(JsonNode root, String field, Duration fallback) {
        String value = readText(root, field, null);
        return value == null ? fallback : Duration.parse(value);
    }

    private static String readText(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Field " + field + " must be a string");
        }
        return node.textValue();
    }

    private static long readLong(JsonNode root, String field, long fallback) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (!node.canConvertToLong()) {
            throw new IllegalArgumentException("Field " + field + " must be a long");
        }
        return node.longValue();
    }

    private static TokenPersistencePolicy readPolicy(JsonNode root, String field, TokenPersistencePolicy fallback) {
        String value = readText(root, field, null);
        return value == null ? fallback : TokenPersistencePolicy.valueOf(value);
    }
}
