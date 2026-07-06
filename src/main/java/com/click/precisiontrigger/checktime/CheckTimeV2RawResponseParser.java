package com.click.precisiontrigger.checktime;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface CheckTimeV2RawResponseParser {
    CheckTimeV2RawResponse parse(String responseBody) throws JsonProcessingException;
}
