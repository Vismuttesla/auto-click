package com.click.precisiontrigger.auth;

import java.util.Optional;

public interface AuthorizationHeaderProvider {
    Optional<String> authorizationHeader();
}
