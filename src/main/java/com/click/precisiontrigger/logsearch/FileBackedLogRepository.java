package com.click.precisiontrigger.logsearch;

import java.nio.file.Path;
import java.util.Objects;

public final class FileBackedLogRepository implements SearchableLogRepository {
    private final Path directory;
    private final LogIndexBuilder logIndexBuilder;

    public FileBackedLogRepository(Path directory, LogIndexBuilder logIndexBuilder) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.logIndexBuilder = Objects.requireNonNull(logIndexBuilder, "logIndexBuilder");
    }

    @Override
    public LogIndex rebuildIndex() {
        return logIndexBuilder.build(directory);
    }
}