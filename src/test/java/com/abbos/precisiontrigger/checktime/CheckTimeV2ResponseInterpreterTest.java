package com.abbos.precisiontrigger.checktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CheckTimeV2ResponseInterpreterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CheckTimeV2ResponseContract contract = CheckTimeV2ResponseContract.productionDefaults();
    private final CheckTimeV2ResponseInterpreter interpreter = new CheckTimeV2ResponseInterpreter(objectMapper, contract);
    private final JacksonCheckTimeV2RawResponseParser rawParser = new JacksonCheckTimeV2RawResponseParser(objectMapper);

    @Test
    void bindsObservedTransportModelWithoutUsingFloatingPoint() throws Exception {
        CheckTimeV2RawResponse response = rawParser.parse("""
                {
                  "error": null,
                  "success": "true",
                  "data": 1783169575987.4
                }
                """);

        assertThat(response.error()).isNotNull();
        assertThat(response.error().isNull()).isTrue();
        assertThat(response.success()).isEqualTo("true");
        assertThat(response.data()).isEqualByComparingTo("1783169575987.4");
    }

    @Test
    void acceptsObservedProductionSuccessResponse() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": null,
                  "success": "true",
                  "data": 1783169575987.4
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.SUCCESS);
        assertThat(result.serverTime()).isEqualTo(Instant.ofEpochSecond(1783169575L, 987_400_000L));
        assertThat(result.rawTimestamp()).isEqualByComparingTo("1783169575987.4");
    }

    @Test
    void reportsRemoteFailureWhenSuccessValueDoesNotMatchObservedContract() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": {"message": "provider rejected request"},
                  "success": "false",
                  "data": 1783169575987.4
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.REMOTE_OPERATION_REPORTED_FAILURE);
        assertThat(result.serverTime()).isNull();
        assertThat(result.remoteError()).isNotNull();
    }

    @Test
    void rejectsNullData() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": null,
                  "success": "true",
                  "data": null
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.INVALID_RESPONSE);
    }

    @Test
    void rejectsMissingData() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": null,
                  "success": "true"
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.INVALID_RESPONSE);
    }

    @Test
    void rejectsUnexpectedStringData() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": null,
                  "success": "true",
                  "data": "1783169575987.4"
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.INVALID_RESPONSE);
    }

    @Test
    void rejectsMalformedNumericField() {
        CheckTimeV2Result result = interpreter.interpret(200, """
                {
                  "error": null,
                  "success": "true",
                  "data": "not-a-number"
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.INVALID_RESPONSE);
    }

    @Test
    void rejectsNonSuccessHttpStatusCodesBeforeParsingContract() {
        CheckTimeV2Result result = interpreter.interpret(500, """
                {
                  "error": null,
                  "success": "true",
                  "data": 1783169575987.4
                }
                """);

        assertThat(result.status()).isEqualTo(CheckTimeV2ResultStatus.TRANSPORT_FAILURE);
    }
}