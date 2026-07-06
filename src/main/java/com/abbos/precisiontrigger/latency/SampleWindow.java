package com.abbos.precisiontrigger.latency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class SampleWindow {
    private final int capacity;
    private final Deque<LatencySample> samples;

    public SampleWindow(int capacity) {
        this.capacity = capacity;
        this.samples = new ArrayDeque<>(capacity);
    }

    public synchronized void add(LatencySample sample) {
        if (samples.size() == capacity) {
            samples.removeFirst();
        }
        samples.addLast(sample);
    }

    public synchronized SampleWindowSnapshot snapshot() {
        return new SampleWindowSnapshot(List.copyOf(new ArrayList<>(samples)));
    }
}