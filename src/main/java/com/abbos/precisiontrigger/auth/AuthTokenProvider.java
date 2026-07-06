package com.abbos.precisiontrigger.auth;

import java.util.Optional;

public interface AuthTokenProvider {
    Optional<String> currentBearerToken();

    AuthTokenSnapshot snapshot();

    AuthStatusSnapshot status();
}