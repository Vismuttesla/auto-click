package com.abbos.precisiontrigger.logsearch;

import java.util.List;

public record LogSearchResult(List<LogIndexEntry> entries, long totalMatches, int page, int pageSize) {
}