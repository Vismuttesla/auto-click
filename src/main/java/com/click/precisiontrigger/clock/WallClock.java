package com.click.precisiontrigger.clock;

import java.time.Instant;

public interface WallClock {
    Instant now();
}