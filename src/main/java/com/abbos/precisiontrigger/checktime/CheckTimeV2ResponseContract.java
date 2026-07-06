package com.abbos.precisiontrigger.checktime;

import com.abbos.precisiontrigger.time.TimestampFormat;
import com.abbos.precisiontrigger.time.TimestampSemantics;
import com.fasterxml.jackson.core.JsonPointer;

public record CheckTimeV2ResponseContract(
        JsonPointer timestampJsonPointer,
        TimestampFormat timestampFormat,
        TimestampSemantics timestampSemantics,
        JsonPointer successJsonPointer,
        JsonPointer errorJsonPointer,
        String expectedSuccessValue) {

    public static CheckTimeV2ResponseContract productionDefaults() {
        return new CheckTimeV2ResponseContract(
                JsonPointer.compile("/data"),
                TimestampFormat.EPOCH_MILLIS_DECIMAL,
                TimestampSemantics.UNKNOWN,
                JsonPointer.compile("/success"),
                JsonPointer.compile("/error"),
                "true"
        );
    }
}
