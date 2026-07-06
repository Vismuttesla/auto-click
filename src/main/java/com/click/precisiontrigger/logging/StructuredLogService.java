package com.click.precisiontrigger.logging;

import java.time.Duration;

public interface StructuredLogService extends AutoCloseable {
    boolean log(LogEvent event);

    long droppedEvents();

    void flush(Duration timeout);

    @Override
    void close();
}
