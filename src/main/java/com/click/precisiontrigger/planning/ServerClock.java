package com.click.precisiontrigger.planning;

import java.time.Instant;

public interface ServerClock {
    Instant now();

    ServerClockSnapshot snapshot();
}