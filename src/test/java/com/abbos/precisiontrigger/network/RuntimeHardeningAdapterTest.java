package com.abbos.precisiontrigger.network;

import com.abbos.precisiontrigger.sync.SyncStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeHardeningAdapterTest {
    @Test
    void handlesNetworkChangeByInvalidatingAndRequestingFreshSync() {
        RecordingHooks hooks = new RecordingHooks();
        List<RuntimeHardeningEvent> refreshedEvents = new ArrayList<>();
        RuntimeHardeningAdapter adapter = new RuntimeHardeningAdapter(
                hooks,
                event -> {
                    refreshedEvents.add(event);
                    return SyncStatus.SUCCESS;
                },
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));

        RuntimeHardeningEvent event = new RuntimeHardeningEvent(
                RuntimeHardeningEventType.NETWORK_ENVIRONMENT_CHANGED,
                Instant.parse("2026-07-06T06:00:00Z"),
                "vpn-monitor",
                Map.of("localAddress", "10.8.0.2"));

        RuntimeHardeningResult result = adapter.handle(event);

        assertThat(result.decision()).isEqualTo(RuntimeHardeningResultDecision.HANDLED);
        assertThat(result.refreshStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(refreshedEvents).containsExactly(event);
        assertThat(hooks.beforeEvents).containsExactly(event);
        assertThat(hooks.afterEvents).containsExactly(new HookCompletion(event, SyncStatus.SUCCESS));
    }

    @Test
    void suppressesDuplicateSignalsInsideSuppressionWindow() {
        RecordingHooks hooks = new RecordingHooks();
        List<RuntimeHardeningEvent> refreshedEvents = new ArrayList<>();
        RuntimeHardeningAdapter adapter = new RuntimeHardeningAdapter(
                hooks,
                event -> {
                    refreshedEvents.add(event);
                    return SyncStatus.SUCCESS;
                },
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));

        RuntimeHardeningEvent first = new RuntimeHardeningEvent(
                RuntimeHardeningEventType.NETWORK_ENVIRONMENT_CHANGED,
                Instant.parse("2026-07-06T06:00:00Z"),
                "vpn-monitor",
                Map.of());
        RuntimeHardeningEvent second = new RuntimeHardeningEvent(
                RuntimeHardeningEventType.NETWORK_ENVIRONMENT_CHANGED,
                Instant.parse("2026-07-06T06:00:09Z"),
                "vpn-monitor",
                Map.of());

        RuntimeHardeningResult firstResult = adapter.handle(first);
        RuntimeHardeningResult secondResult = adapter.handle(second);

        assertThat(firstResult.decision()).isEqualTo(RuntimeHardeningResultDecision.HANDLED);
        assertThat(secondResult.decision()).isEqualTo(RuntimeHardeningResultDecision.SUPPRESSED);
        assertThat(secondResult.refreshStatus()).isNull();
        assertThat(refreshedEvents).containsExactly(first);
        assertThat(hooks.beforeEvents).containsExactly(first);
    }

    @Test
    void acceptsSameSourceAgainAfterSuppressionWindowExpires() {
        RecordingHooks hooks = new RecordingHooks();
        List<RuntimeHardeningEvent> refreshedEvents = new ArrayList<>();
        RuntimeHardeningAdapter adapter = new RuntimeHardeningAdapter(
                hooks,
                event -> {
                    refreshedEvents.add(event);
                    return SyncStatus.SUCCESS;
                },
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));

        RuntimeHardeningEvent first = new RuntimeHardeningEvent(
                RuntimeHardeningEventType.SYSTEM_RESUMED,
                Instant.parse("2026-07-06T06:00:00Z"),
                "scheduler-gap",
                Map.of("observedGap", "PT45S"));
        RuntimeHardeningEvent second = new RuntimeHardeningEvent(
                RuntimeHardeningEventType.SYSTEM_RESUMED,
                Instant.parse("2026-07-06T06:00:31Z"),
                "scheduler-gap",
                Map.of("observedGap", "PT46S"));

        RuntimeHardeningResult firstResult = adapter.handle(first);
        RuntimeHardeningResult secondResult = adapter.handle(second);

        assertThat(firstResult.decision()).isEqualTo(RuntimeHardeningResultDecision.HANDLED);
        assertThat(secondResult.decision()).isEqualTo(RuntimeHardeningResultDecision.HANDLED);
        assertThat(refreshedEvents).containsExactly(first, second);
    }

    @Test
    void systemResumeConvenienceAddsObservedGapMetadata() {
        RecordingHooks hooks = new RecordingHooks();
        RuntimeHardeningAdapter adapter = new RuntimeHardeningAdapter(
                hooks,
                event -> SyncStatus.SUCCESS,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));

        RuntimeHardeningResult result = adapter.systemResumed(
                Instant.parse("2026-07-06T06:00:00Z"),
                "scheduler-gap",
                Duration.ofMinutes(2),
                Map.of("resumeReason", "long-gap"));

        assertThat(result.decision()).isEqualTo(RuntimeHardeningResultDecision.HANDLED);
        assertThat(result.event().attributes())
                .containsEntry("resumeReason", "long-gap")
                .containsEntry("observedGap", "PT2M");
    }

    private static final class RecordingHooks implements RuntimeHardeningHooks {
        private final List<RuntimeHardeningEvent> beforeEvents = new ArrayList<>();
        private final List<HookCompletion> afterEvents = new ArrayList<>();

        @Override
        public void beforeRefresh(RuntimeHardeningEvent event) {
            beforeEvents.add(event);
        }

        @Override
        public void afterRefresh(RuntimeHardeningEvent event, SyncStatus status) {
            afterEvents.add(new HookCompletion(event, status));
        }
    }

    private record HookCompletion(RuntimeHardeningEvent event, SyncStatus status) {
    }
}
