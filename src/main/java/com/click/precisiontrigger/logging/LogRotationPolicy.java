package com.click.precisiontrigger.logging;

import java.nio.file.Path;
import java.time.Instant;

public interface LogRotationPolicy {
    Path resolvePath(Path directory, Instant timestamp);
}
