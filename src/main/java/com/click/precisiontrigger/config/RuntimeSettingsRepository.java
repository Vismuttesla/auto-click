package com.click.precisiontrigger.config;

public interface RuntimeSettingsRepository {
    RuntimeSettings load();

    void save(RuntimeSettings settings);
}
