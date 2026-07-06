package com.click.precisiontrigger.checktime;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckTimeV2Result(
        CheckTimeV2ResultStatus status,
        Instant serverTime,
        BigDecimal rawTimestamp,
        JsonNode remoteError,
        String diagnostic) {

    public static CheckTimeV2Result success(Instant serverTime, BigDecimal rawTimestamp, JsonNode remoteError) {
        return new CheckTimeV2Result(CheckTimeV2ResultStatus.SUCCESS, serverTime, rawTimestamp, remoteError, null);
    }

    public static CheckTimeV2Result remoteFailure(JsonNode remoteError, String diagnostic) {
        return new CheckTimeV2Result(CheckTimeV2ResultStatus.REMOTE_OPERATION_REPORTED_FAILURE, null, null, remoteError, diagnostic);
    }

    public static CheckTimeV2Result invalidResponse(JsonNode remoteError, String diagnostic) {
        return new CheckTimeV2Result(CheckTimeV2ResultStatus.INVALID_RESPONSE, null, null, remoteError, diagnostic);
    }

    public static CheckTimeV2Result transportFailure(String diagnostic) {
        return new CheckTimeV2Result(CheckTimeV2ResultStatus.TRANSPORT_FAILURE, null, null, null, diagnostic);
    }
}
