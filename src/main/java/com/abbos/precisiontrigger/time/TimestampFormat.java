package com.abbos.precisiontrigger.time;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public enum TimestampFormat {
    EPOCH_MILLIS_DECIMAL {
        @Override
        public Instant toInstant(JsonNode node, RoundingMode roundingMode) {
            requireNumericNode(node, "EPOCH_MILLIS_DECIMAL");
            return new DecimalEpochMillisInstantConverter(roundingMode).convert(node.decimalValue());
        }
    },
    EPOCH_MILLIS {
        @Override
        public Instant toInstant(JsonNode node, RoundingMode roundingMode) {
            requireNumericNode(node, "EPOCH_MILLIS");
            return Instant.ofEpochMilli(node.decimalValue().toBigIntegerExact().longValueExact());
        }
    },
    EPOCH_SECONDS {
        @Override
        public Instant toInstant(JsonNode node, RoundingMode roundingMode) {
            requireNumericNode(node, "EPOCH_SECONDS");
            BigDecimal seconds = node.decimalValue();
            BigDecimal nanos = seconds.movePointRight(9).setScale(0, roundingMode);
            return DecimalEpochMillisInstantConverter.fromEpochNanos(nanos);
        }
    },
    ISO_8601 {
        @Override
        public Instant toInstant(JsonNode node, RoundingMode roundingMode) {
            if (node == null || !node.isTextual()) {
                throw new IllegalArgumentException("ISO_8601 timestamp requires a text node");
            }
            return Instant.parse(node.textValue());
        }
    };

    public abstract Instant toInstant(JsonNode node, RoundingMode roundingMode);

    private static void requireNumericNode(JsonNode node, String formatName) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException(formatName + " timestamp requires a numeric node");
        }
    }
}

