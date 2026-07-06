package com.click.precisiontrigger.network;

import com.click.precisiontrigger.sync.SyncStatus;

@FunctionalInterface
public interface RuntimeRefreshRequester {
    SyncStatus requestFreshSync(RuntimeHardeningEvent event);
}
