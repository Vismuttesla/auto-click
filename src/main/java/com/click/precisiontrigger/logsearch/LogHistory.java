package com.click.precisiontrigger.logsearch;

import java.io.IOException;

public interface LogHistory {
    LogSearchPage search(LogSearchQuery query) throws IOException;
}
