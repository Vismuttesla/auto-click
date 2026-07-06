package com.click.precisiontrigger.latency;

import java.util.List;

public record SampleWindowSnapshot(List<LatencySample> samples) {
}