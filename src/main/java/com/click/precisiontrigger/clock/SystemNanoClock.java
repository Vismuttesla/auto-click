package com.click.precisiontrigger.clock;

public final class SystemNanoClock implements NanoClock {
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}