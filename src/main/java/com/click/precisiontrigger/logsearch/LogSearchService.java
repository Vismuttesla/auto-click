package com.click.precisiontrigger.logsearch;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

public final class LogSearchService {
    private final SearchableLogRepository repository;

    public LogSearchService(SearchableLogRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public LogSearchResult search(LogSearchQuery query) {
        LogSearchQuery effective = query == null ? LogSearchQuery.defaults() : query;
        Predicate<LogIndexEntry> predicate = buildPredicate(effective);
        Comparator<LogIndexEntry> comparator = Comparator.comparing(LogIndexEntry::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LogIndexEntry::lineNumber);
        if (effective.order() == LogSearchOrder.NEWEST_FIRST) {
            comparator = comparator.reversed();
        }
        List<LogIndexEntry> filtered = repository.rebuildIndex().entries().stream()
                .filter(predicate)
                .sorted(comparator)
                .toList();
        int fromIndex = Math.min(filtered.size(), effective.page() * effective.pageSize());
        int toIndex = Math.min(filtered.size(), fromIndex + effective.pageSize());
        return new LogSearchResult(filtered.subList(fromIndex, toIndex), filtered.size(), effective.page(), effective.pageSize());
    }

    private Predicate<LogIndexEntry> buildPredicate(LogSearchQuery query) {
        return entry -> withinRange(entry, query)
                && matchesText(entry.eventType(), query.eventType())
                && matchesLong(entry.sequence(), query.sequence())
                && matchesMin(entry.s1Nanos(), query.minS1Nanos())
                && matchesMax(entry.s1Nanos(), query.maxS1Nanos())
                && matchesMin(entry.s2Nanos(), query.minS2Nanos())
                && matchesMax(entry.s2Nanos(), query.maxS2Nanos())
                && matchesMin(entry.rttNanos(), query.minRttNanos())
                && matchesMax(entry.rttNanos(), query.maxRttNanos())
                && matchesDoubleMin(entry.confidence(), query.minConfidence())
                && matchesBoolean(entry.accepted(), query.accepted())
                && matchesText(entry.estimationStrategy(), query.estimationStrategy())
                && matchesFreeText(entry, query.freeText());
    }

    private boolean withinRange(LogIndexEntry entry, LogSearchQuery query) {
        if (entry.timestamp() == null) {
            return query.from() == null && query.to() == null;
        }
        if (query.from() != null && entry.timestamp().isBefore(query.from())) {
            return false;
        }
        return query.to() == null || !entry.timestamp().isAfter(query.to());
    }

    private static boolean matchesText(String value, String expected) {
        return expected == null || expected.isBlank() || (value != null && value.equalsIgnoreCase(expected));
    }

    private static boolean matchesLong(Long value, Long expected) {
        return expected == null || Objects.equals(value, expected);
    }

    private static boolean matchesMin(Long value, Long min) {
        return min == null || (value != null && value >= min);
    }

    private static boolean matchesMax(Long value, Long max) {
        return max == null || (value != null && value <= max);
    }

    private static boolean matchesDoubleMin(Double value, Double min) {
        return min == null || (value != null && value >= min);
    }

    private static boolean matchesBoolean(Boolean value, Boolean expected) {
        return expected == null || Objects.equals(value, expected);
    }

    private static boolean matchesFreeText(LogIndexEntry entry, String freeText) {
        return freeText == null || freeText.isBlank() || entry.rawLine().toLowerCase(Locale.ROOT).contains(freeText.toLowerCase(Locale.ROOT));
    }
}