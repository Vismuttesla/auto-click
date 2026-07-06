package com.abbos.precisiontrigger.network;

import com.abbos.precisiontrigger.sync.SyncStatus;

public record RuntimeHardeningResult(
        RuntimeHardeningResultDecision decision,
        RuntimeHardeningEvent event,
        SyncStatus refreshStatus) {
}
