package com.click.precisiontrigger.sync;

import com.click.precisiontrigger.config.FileBackedRuntimeSettingsRepository;
import com.click.precisiontrigger.config.RuntimeSettings;
import com.click.precisiontrigger.config.TimingConfig;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncIntervalServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsUpdatedRuntimeIntervalAndConfigurationVersion() {
        FileBackedRuntimeSettingsRepository repository = new FileBackedRuntimeSettingsRepository(tempDir.resolve("runtime-settings.json"), new ObjectMapper().registerModule(new JavaTimeModule()));
        SyncIntervalService service = new SyncIntervalService(
                new TimingConfig(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofHours(1), 30, Duration.ofMinutes(3), 0.70, Duration.ofSeconds(2), Duration.ofMillis(50), Duration.ofMillis(2), Duration.ZERO),
                repository,
                new JsonlEventLogger(tempDir),
                RuntimeSettings.DEFAULT);

        RuntimeSettings updated = service.updateInterval(Duration.ofSeconds(30));

        assertThat(updated.syncInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(updated.configurationVersion()).isEqualTo(RuntimeSettings.DEFAULT.configurationVersion() + 1);
        assertThat(repository.load().syncInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsBelowMinimumInterval() {
        FileBackedRuntimeSettingsRepository repository = new FileBackedRuntimeSettingsRepository(tempDir.resolve("runtime-settings.json"), new ObjectMapper().registerModule(new JavaTimeModule()));
        SyncIntervalService service = new SyncIntervalService(
                new TimingConfig(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofHours(1), 30, Duration.ofMinutes(3), 0.70, Duration.ofSeconds(2), Duration.ofMillis(50), Duration.ofMillis(2), Duration.ZERO),
                repository,
                new JsonlEventLogger(tempDir),
                RuntimeSettings.DEFAULT);

        assertThatThrownBy(() -> service.updateInterval(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below minimum");
    }
}