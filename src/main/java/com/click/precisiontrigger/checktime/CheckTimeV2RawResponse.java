package com.click.precisiontrigger.checktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckTimeV2RawResponse(JsonNode error, String success, BigDecimal data) {
}
