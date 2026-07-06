package com.abbos.precisiontrigger.time;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DecimalEpochMillisInstantConverterTest {
    private final DecimalEpochMillisInstantConverter converter = new DecimalEpochMillisInstantConverter(RoundingMode.HALF_UP);

    @Test
    void convertsIntegerMillisecondsExactly() {
        assertThat(converter.convert(new BigDecimal("1783169575987")))
                .isEqualTo(Instant.ofEpochSecond(1783169575L, 987_000_000L));
    }

    @Test
    void convertsFractionalMillisecondsExactly() {
        assertThat(converter.convert(new BigDecimal("1783169575987.4")))
                .isEqualTo(Instant.ofEpochSecond(1783169575L, 987_400_000L));
    }

    @Test
    void convertsThreeDecimalFractionalMillisecondsExactly() {
        assertThat(converter.convert(new BigDecimal("1783169575987.123")))
                .isEqualTo(Instant.ofEpochSecond(1783169575L, 987_123_000L));
    }

    @Test
    void convertsMaximumUsefulNanosecondPrecision() {
        assertThat(converter.convert(new BigDecimal("1.123456")))
                .isEqualTo(Instant.ofEpochSecond(0L, 1_123_456L));
    }

    @Test
    void convertsZero() {
        assertThat(converter.convert(BigDecimal.ZERO))
                .isEqualTo(Instant.EPOCH);
    }

    @Test
    void convertsNegativeValues() {
        assertThat(converter.convert(new BigDecimal("-1.25")))
                .isEqualTo(Instant.ofEpochSecond(-1L, 998_750_000L));
    }

    @Test
    void roundsExcessiveFractionalDigitsUsingConfiguredPolicy() {
        assertThat(converter.convert(new BigDecimal("1.0000004")))
                .isEqualTo(Instant.ofEpochSecond(0L, 1_000_000L));
        assertThat(converter.convert(new BigDecimal("1.0000005")))
                .isEqualTo(Instant.ofEpochSecond(0L, 1_000_001L));
    }
}