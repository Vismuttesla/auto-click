package com.abbos.precisiontrigger.latency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MadOutlierFilter implements OutlierFilter {
    @Override
    public boolean isOutlier(Duration candidateRtt, SampleWindowSnapshot history) {
        List<Long> values = history.samples().stream()
                .filter(LatencySample::accepted)
                .map(sample -> sample.rtt().toNanos())
                .sorted()
                .toList();
        if (values.size() < 3) {
            return false;
        }
        long median = values.get(values.size() / 2);
        List<Long> deviations = new ArrayList<>(values.size());
        for (Long value : values) {
            deviations.add(Math.abs(value - median));
        }
        deviations.sort(Comparator.naturalOrder());
        long mad = deviations.get(deviations.size() / 2);
        if (mad == 0L) {
            return false;
        }
        return Math.abs(candidateRtt.toNanos() - median) > mad * 6L;
    }
}