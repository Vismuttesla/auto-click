package com.abbos.precisiontrigger.config;

public interface RuntimeSettingsRepository {
    RuntimeSettings load();

    void save(RuntimeSettings settings);
}
