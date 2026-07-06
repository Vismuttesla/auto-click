package com.click.precisiontrigger.logging;

import com.click.precisiontrigger.config.FileBackedRuntimeSettingsRepository;
import com.click.precisiontrigger.config.RuntimeSettings;
import com.click.precisiontrigger.config.TokenPersistencePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FileBackedRuntimeSettingsRepositoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void loadsDefaultsWhenSettingsFileDoesNotExist() {
        FileBackedRuntimeSettingsRepository repository =
                new FileBackedRuntimeSettingsRepository(tempDir.resolve("config/runtime-settings.json"), objectMapper);

        RuntimeSettings settings = repository.load();

        assertThat(settings).isEqualTo(RuntimeSettings.DEFAULT);
    }

    @Test
    void savesAndReloadsRuntimeSettingsWithoutPersistingTokenFields() throws Exception {
        Path path = tempDir.resolve("config/runtime-settings.json");
        FileBackedRuntimeSettingsRepository repository = new FileBackedRuntimeSettingsRepository(path, objectMapper);
        RuntimeSettings settings = new RuntimeSettings(Duration.ofSeconds(30), "Asia/Tashkent", 12L, TokenPersistencePolicy.MEMORY_ONLY);

        repository.save(settings);
        RuntimeSettings reloaded = repository.load();
        String json = Files.readString(path, StandardCharsets.UTF_8);

        assertThat(reloaded).isEqualTo(settings);
        assertThat(json).contains("\"syncInterval\" : \"PT30S\"");
        assertThat(json).contains("\"displayZoneId\" : \"Asia/Tashkent\"");
        assertThat(json).contains("\"tokenPersistencePolicy\" : \"MEMORY_ONLY\"");
        assertThat(json).doesNotContain("Bearer ").doesNotContain("rawToken").doesNotContain("authorizationHeader");
    }
}
