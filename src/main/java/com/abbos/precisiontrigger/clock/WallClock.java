package com.abbos.precisiontrigger.clock;

import java.time.Instant;

public interface WallClock {
    Instant now();
}