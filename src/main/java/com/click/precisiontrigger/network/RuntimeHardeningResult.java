package com.click.precisiontrigger.network;

import com.click.precisiontrigger.sync.SyncStatus;

public record RuntimeHardeningResult(
        RuntimeHardeningResultDecision decision,
        RuntimeHardeningEvent event,
        SyncStatus refreshStatus) {
}
