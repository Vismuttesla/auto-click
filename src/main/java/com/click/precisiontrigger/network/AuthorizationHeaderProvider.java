package com.click.precisiontrigger.network;

import java.util.Optional;

public interface AuthorizationHeaderProvider {
    Optional<String> authorizationHeader();
}