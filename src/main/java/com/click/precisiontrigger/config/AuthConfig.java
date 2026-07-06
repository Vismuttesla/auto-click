package com.click.precisiontrigger.config;

import java.time.Duration;

public record AuthConfig(Duration expiringSoonThreshold, Duration unauthorizedBackoff) {
}