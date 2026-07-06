package com.click.precisiontrigger.network;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public interface RuntimeHardeningSignalSink {
    RuntimeHardeningResult handle(RuntimeHardeningEvent event);

    default RuntimeHardeningResult networkEnvironmentChanged(Instant detectedAt, String source, Map<String, String> attributes) {
        return handle(new RuntimeHardeningEvent(
                RuntimeHardeningEventType.NETWORK_ENVIRONMENT_CHANGED,
                detectedAt,
                source,
                attributes));
    }

    default RuntimeHardeningResult systemResumed(Instant detectedAt, String source, Duration observedGap, Map<String, String> attributes) {
        Map<String, String> metadata = new HashMap<>(attributes);
        metadata.put("observedGap", observedGap.toString());
        return handle(new RuntimeHardeningEvent(
                RuntimeHardeningEventType.SYSTEM_RESUMED,
                detectedAt,
                source,
                metadata));
    }
}
