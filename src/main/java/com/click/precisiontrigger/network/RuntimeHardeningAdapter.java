package com.click.precisiontrigger.network;

import com.click.precisiontrigger.sync.SyncStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RuntimeHardeningAdapter implements RuntimeHardeningSignalSink {
    private final RuntimeHardeningHooks hooks;
    private final RuntimeRefreshRequester refreshRequester;
    private final Duration networkChangeSuppressionWindow;
    private final Duration systemResumeSuppressionWindow;
    private final ConcurrentMap<SuppressionKey, Instant> lastHandledAt = new ConcurrentHashMap<>();

    public RuntimeHardeningAdapter(RuntimeHardeningHooks hooks,
                                   RuntimeRefreshRequester refreshRequester,
                                   Duration networkChangeSuppressionWindow,
                                   Duration systemResumeSuppressionWindow) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.refreshRequester = Objects.requireNonNull(refreshRequester, "refreshRequester");
        this.networkChangeSuppressionWindow = validateWindow(networkChangeSuppressionWindow, "networkChangeSuppressionWindow");
        this.systemResumeSuppressionWindow = validateWindow(systemResumeSuppressionWindow, "systemResumeSuppressionWindow");
    }

    @Override
    public RuntimeHardeningResult handle(RuntimeHardeningEvent event) {
        Objects.requireNonNull(event, "event");
        if (shouldSuppress(event)) {
            return new RuntimeHardeningResult(RuntimeHardeningResultDecision.SUPPRESSED, event, null);
        }
        hooks.beforeRefresh(event);
        SyncStatus status = refreshRequester.requestFreshSync(event);
        hooks.afterRefresh(event, status);
        return new RuntimeHardeningResult(RuntimeHardeningResultDecision.HANDLED, event, status);
    }

    private boolean shouldSuppress(RuntimeHardeningEvent event) {
        Duration window = suppressionWindow(event.type());
        SuppressionKey key = new SuppressionKey(event.type(), event.source());
        if (window.isZero()) {
            lastHandledAt.put(key, event.detectedAt());
            return false;
        }
        Instant previous = lastHandledAt.get(key);
        if (previous != null && !event.detectedAt().isAfter(previous.plus(window))) {
            return true;
        }
        lastHandledAt.put(key, event.detectedAt());
        return false;
    }

    private Duration suppressionWindow(RuntimeHardeningEventType type) {
        return switch (type) {
            case NETWORK_ENVIRONMENT_CHANGED -> networkChangeSuppressionWindow;
            case SYSTEM_RESUMED -> systemResumeSuppressionWindow;
        };
    }

    private static Duration validateWindow(Duration window, String name) {
        Objects.requireNonNull(window, name);
        if (window.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return window;
    }

    private record SuppressionKey(RuntimeHardeningEventType type, String source) {
        private SuppressionKey {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
        }
    }
}
