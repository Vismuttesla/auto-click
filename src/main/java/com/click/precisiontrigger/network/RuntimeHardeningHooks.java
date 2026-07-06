package com.click.precisiontrigger.network;

import com.click.precisiontrigger.sync.SyncStatus;

public interface RuntimeHardeningHooks {
    void beforeRefresh(RuntimeHardeningEvent event);

    void afterRefresh(RuntimeHardeningEvent event, SyncStatus status);
}
