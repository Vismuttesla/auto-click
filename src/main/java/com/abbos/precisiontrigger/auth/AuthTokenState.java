package com.abbos.precisiontrigger.auth;

public enum AuthTokenState {
    NO_TOKEN,
    TOKEN_PRESENT,
    TOKEN_EXPIRING_SOON,
    TOKEN_EXPIRED,
    AUTHENTICATED,
    AUTH_REJECTED,
    FORBIDDEN,
    UNKNOWN
}