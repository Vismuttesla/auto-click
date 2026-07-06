package com.click.precisiontrigger.network;

import com.click.precisiontrigger.auth.AuthTokenProvider;

import java.util.Objects;
import java.util.Optional;

public final class DefaultAuthorizationHeaderProvider implements AuthorizationHeaderProvider {
    private final AuthTokenProvider authTokenProvider;

    public DefaultAuthorizationHeaderProvider(AuthTokenProvider authTokenProvider) {
        this.authTokenProvider = Objects.requireNonNull(authTokenProvider, "authTokenProvider");
    }

    @Override
    public Optional<String> authorizationHeader() {
        return authTokenProvider.currentBearerToken().map(token -> "Bearer " + token);
    }
}