package com.click.precisiontrigger.client;

import com.click.precisiontrigger.time.TimeMeasurement;

public record ServerTimeClientResult(
        boolean success,
        TimeMeasurement measurement,
        ServerTimeFailureCode failureCode,
        String safeMessage) {

    public static ServerTimeClientResult success(TimeMeasurement measurement) {
        return new ServerTimeClientResult(true, measurement, null, null);
    }

    public static ServerTimeClientResult failure(ServerTimeFailureCode failureCode, String safeMessage) {
        return new ServerTimeClientResult(false, null, failureCode, safeMessage);
    }
}