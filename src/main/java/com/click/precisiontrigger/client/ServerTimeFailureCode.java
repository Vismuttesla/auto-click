package com.click.precisiontrigger.client;

public enum ServerTimeFailureCode {
    AUTH_TOKEN_MISSING,
    AUTH_BACKOFF_ACTIVE,
    UNAUTHORIZED,
    FORBIDDEN,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    REMOTE_OPERATION_REPORTED_FAILURE,
    UNEXPECTED_STATUS
}