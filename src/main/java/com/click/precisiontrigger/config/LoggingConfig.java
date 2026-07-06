package com.click.precisiontrigger.config;

import java.nio.file.Path;

public record LoggingConfig(Path directory, int asyncQueueCapacity, boolean indexOnStartup) {
}