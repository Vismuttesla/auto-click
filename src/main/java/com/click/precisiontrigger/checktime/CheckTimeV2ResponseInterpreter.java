package com.click.precisiontrigger.checktime;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public final class CheckTimeV2ResponseInterpreter {
    private final ObjectMapper objectMapper;
    private final CheckTimeV2ResponseContract contract;
    private final RoundingMode roundingMode;

    public CheckTimeV2ResponseInterpreter(ObjectMapper objectMapper, CheckTimeV2ResponseContract contract) {
        this(objectMapper, contract, RoundingMode.HALF_UP);
    }

    public CheckTimeV2ResponseInterpreter(ObjectMapper objectMapper, CheckTimeV2ResponseContract contract, RoundingMode roundingMode) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode");
    }

    public CheckTimeV2Result interpret(int httpStatusCode, String responseBody) {
        if (httpStatusCode < 200 || httpStatusCode >= 300) {
            return CheckTimeV2Result.transportFailure("HTTP status was " + httpStatusCode);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            return CheckTimeV2Result.invalidResponse(null, "Failed to parse JSON response: " + ex.getMessage());
        }

        JsonNode successNode = root.at(contract.successJsonPointer());
        if (successNode.isMissingNode() || successNode.isNull()) {
            return CheckTimeV2Result.invalidResponse(extractSafeNode(root, contract.errorJsonPointer()), "Missing success field");
        }
        if (!successNode.isTextual()) {
            return CheckTimeV2Result.invalidResponse(extractSafeNode(root, contract.errorJsonPointer()), "Success field must be a string");
        }

        String successValue = successNode.textValue();
        if (!contract.expectedSuccessValue().equals(successValue)) {
            return CheckTimeV2Result.remoteFailure(extractSafeNode(root, contract.errorJsonPointer()), "Remote success value was " + successValue);
        }

        JsonNode dataNode = root.at(contract.timestampJsonPointer());
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            return CheckTimeV2Result.invalidResponse(extractSafeNode(root, contract.errorJsonPointer()), "Missing timestamp data");
        }
        if (!dataNode.isNumber()) {
            return CheckTimeV2Result.invalidResponse(extractSafeNode(root, contract.errorJsonPointer()), "Timestamp data must be numeric");
        }

        Instant serverTime;
        try {
            serverTime = contract.timestampFormat().toInstant(dataNode, roundingMode);
        } catch (RuntimeException ex) {
            return CheckTimeV2Result.invalidResponse(extractSafeNode(root, contract.errorJsonPointer()), "Timestamp conversion failed: " + ex.getMessage());
        }

        return CheckTimeV2Result.success(serverTime, dataNode.decimalValue(), extractSafeNode(root, contract.errorJsonPointer()));
    }

    private static JsonNode extractSafeNode(JsonNode root, JsonPointer pointer) {
        JsonNode node = root.at(pointer);
        return node.isMissingNode() ? null : node;
    }
}
