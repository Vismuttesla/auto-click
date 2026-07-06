package com.click.precisiontrigger.time;

import java.math.BigDecimal;
import java.time.Instant;

public record TimeMeasurement(
        long localSendNano,
        long localReceiveNano,
        Instant serverTimestamp,
        Instant localCollectedAt,
        int httpStatus,
        long requestSequence,
        long configurationVersion,
        BigDecimal rawTimestamp) {
}