package com.click.precisiontrigger.config;

public record ApplicationConfig(
        ServerConfig server,
        RequestHeadersConfig request,
        TimingConfig timing,
        AuthConfig auth,
        LoggingConfig logging,
        UiConfig ui) {
}