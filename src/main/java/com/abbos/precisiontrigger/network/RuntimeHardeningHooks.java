package com.abbos.precisiontrigger.network;

import com.abbos.precisiontrigger.sync.SyncStatus;

public interface RuntimeHardeningHooks {
    void beforeRefresh(RuntimeHardeningEvent event);

    void afterRefresh(RuntimeHardeningEvent event, SyncStatus status);
}
