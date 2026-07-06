package com.click.precisiontrigger.time;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Converts decimal epoch milliseconds into {@link Instant} without using floating-point math.
 *
 * <p>Fractional nanoseconds are resolved with the configured {@link RoundingMode}; the default
 * policy is {@link RoundingMode#HALF_UP}. This keeps sub-millisecond precision explicit while
 * avoiding silent precision loss.</p>
 */
public final class DecimalEpochMillisInstantConverter {
    private static final BigDecimal NANOS_PER_SECOND = new BigDecimal("1000000000");

    private final RoundingMode roundingMode;

    public DecimalEpochMillisInstantConverter() {
        this(RoundingMode.HALF_UP);
    }

    public DecimalEpochMillisInstantConverter(RoundingMode roundingMode) {
        this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode");
    }

    public Instant convert(BigDecimal epochMillisDecimal) {
        Objects.requireNonNull(epochMillisDecimal, "epochMillisDecimal");
        BigDecimal totalNanos = epochMillisDecimal.movePointRight(6).setScale(0, roundingMode);
        return fromEpochNanos(totalNanos);
    }

    static Instant fromEpochNanos(BigDecimal totalNanos) {
        Objects.requireNonNull(totalNanos, "totalNanos");
        BigDecimal seconds = totalNanos.divide(NANOS_PER_SECOND, 0, RoundingMode.FLOOR);
        BigDecimal nanos = totalNanos.subtract(seconds.multiply(NANOS_PER_SECOND));
        return Instant.ofEpochSecond(seconds.longValueExact(), nanos.longValueExact());
    }
}