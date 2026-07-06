package com.abbos.precisiontrigger.network;

import com.abbos.precisiontrigger.sync.SyncStatus;

@FunctionalInterface
public interface RuntimeRefreshRequester {
    SyncStatus requestFreshSync(RuntimeHardeningEvent event);
}
