package com.click.precisiontrigger.config;

import com.click.precisiontrigger.time.TimestampFormat;
import com.click.precisiontrigger.time.TimestampSemantics;

import java.time.Duration;

public record ServerConfig(
        String timeUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        TimestampConfig timestamp,
        ResponseContractConfig response) {

    public record TimestampConfig(
            String jsonPointer,
            TimestampFormat format,
            TimestampSemantics semantics) {
    }

    public record ResponseContractConfig(
            String successJsonPointer,
            String errorJsonPointer,
            String expectedSuccessValue) {
    }
}