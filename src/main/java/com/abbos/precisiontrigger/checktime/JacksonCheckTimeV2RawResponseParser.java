package com.abbos.precisiontrigger.checktime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class JacksonCheckTimeV2RawResponseParser implements CheckTimeV2RawResponseParser {
    private final ObjectMapper objectMapper;

    public JacksonCheckTimeV2RawResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public CheckTimeV2RawResponse parse(String responseBody) throws JsonProcessingException {
        return objectMapper.readValue(responseBody, CheckTimeV2RawResponse.class);
    }
}
