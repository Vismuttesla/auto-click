package com.abbos.precisiontrigger.client;

import com.abbos.precisiontrigger.auth.RuntimeAuthTokenService;
import com.abbos.precisiontrigger.checktime.CheckTimeV2ResponseContract;
import com.abbos.precisiontrigger.checktime.CheckTimeV2ResponseInterpreter;
import com.abbos.precisiontrigger.checktime.CheckTimeV2Result;
import com.abbos.precisiontrigger.checktime.CheckTimeV2ResultStatus;
import com.abbos.precisiontrigger.clock.NanoClock;
import com.abbos.precisiontrigger.clock.WallClock;
import com.abbos.precisiontrigger.config.RequestHeadersConfig;
import com.abbos.precisiontrigger.config.ServerConfig;
import com.abbos.precisiontrigger.logging.JsonlEventLogger;
import com.abbos.precisiontrigger.logging.JsonlEventType;
import com.abbos.precisiontrigger.network.AuthorizationHeaderProvider;
import com.abbos.precisiontrigger.time.TimeMeasurement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class HttpServerTimeClient implements ServerTimeClient {
    private final HttpClient httpClient;
    private final ServerConfig serverConfig;
    private final RequestHeadersConfig requestHeadersConfig;
    private final AuthorizationHeaderProvider authorizationHeaderProvider;
    private final RuntimeAuthTokenService runtimeAuthTokenService;
    private final CheckTimeV2ResponseInterpreter responseInterpreter;
    private final NanoClock nanoClock;
    private final WallClock wallClock;
    private final JsonlEventLogger eventLogger;

    public HttpServerTimeClient(HttpClient httpClient,
                                ServerConfig serverConfig,
                                RequestHeadersConfig requestHeadersConfig,
                                AuthorizationHeaderProvider authorizationHeaderProvider,
                                RuntimeAuthTokenService runtimeAuthTokenService,
                                CheckTimeV2ResponseInterpreter responseInterpreter,
                                NanoClock nanoClock,
                                WallClock wallClock,
                                JsonlEventLogger eventLogger) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.serverConfig = Objects.requireNonNull(serverConfig, "serverConfig");
        this.requestHeadersConfig = Objects.requireNonNull(requestHeadersConfig, "requestHeadersConfig");
        this.authorizationHeaderProvider = Objects.requireNonNull(authorizationHeaderProvider, "authorizationHeaderProvider");
        this.runtimeAuthTokenService = Objects.requireNonNull(runtimeAuthTokenService, "runtimeAuthTokenService");
        this.responseInterpreter = Objects.requireNonNull(responseInterpreter, "responseInterpreter");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger");
    }

    public static CheckTimeV2ResponseInterpreter defaultInterpreter(com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                                                    ServerConfig serverConfig) {
        return new CheckTimeV2ResponseInterpreter(objectMapper, new CheckTimeV2ResponseContract(
                com.fasterxml.jackson.core.JsonPointer.compile(serverConfig.timestamp().jsonPointer()),
                serverConfig.timestamp().format(),
                serverConfig.timestamp().semantics(),
                com.fasterxml.jackson.core.JsonPointer.compile(serverConfig.response().successJsonPointer()),
                com.fasterxml.jackson.core.JsonPointer.compile(serverConfig.response().errorJsonPointer()),
                serverConfig.response().expectedSuccessValue()));
    }

    @Override
    public ServerTimeClientResult fetchServerTime(long requestSequence, long configurationVersion) {
        Instant now = wallClock.now();
        if (!runtimeAuthTokenService.canIssueAuthenticatedRequest(now)) {
            return ServerTimeClientResult.failure(ServerTimeFailureCode.AUTH_BACKOFF_ACTIVE, "Authentication backoff is active");
        }
        String authHeader = authorizationHeaderProvider.authorizationHeader().orElse(null);
        if (authHeader == null || authHeader.isBlank()) {
            return ServerTimeClientResult.failure(ServerTimeFailureCode.AUTH_TOKEN_MISSING, "Authentication token is missing");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(serverConfig.timeUrl()))
                .GET()
                .timeout(serverConfig.requestTimeout())
                .header("Accept", requestHeadersConfig.accept())
                .header("Content-Type", requestHeadersConfig.contentType())
                .header("User-Agent", requestHeadersConfig.userAgent())
                .header("Authorization", authHeader)
                .build();

        long localSendNano = nanoClock.nanoTime();
        eventLogger.log(JsonlEventType.TIME_SYNC_STARTED, Map.of("sequence", requestSequence, "configurationVersion", configurationVersion));
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long localReceiveNano = nanoClock.nanoTime();
            if (response.statusCode() == 401) {
                runtimeAuthTokenService.markUnauthorized(wallClock.now(), "Server returned 401 Unauthorized");
                return ServerTimeClientResult.failure(ServerTimeFailureCode.UNAUTHORIZED, "Server returned 401 Unauthorized");
            }
            if (response.statusCode() == 403) {
                runtimeAuthTokenService.markForbidden(wallClock.now(), "Server returned 403 Forbidden");
                return ServerTimeClientResult.failure(ServerTimeFailureCode.FORBIDDEN, "Server returned 403 Forbidden");
            }
            CheckTimeV2Result interpreted = responseInterpreter.interpret(response.statusCode(), response.body());
            if (interpreted.status() == CheckTimeV2ResultStatus.SUCCESS) {
                return ServerTimeClientResult.success(new TimeMeasurement(
                        localSendNano,
                        localReceiveNano,
                        interpreted.serverTime(),
                        wallClock.now(),
                        response.statusCode(),
                        requestSequence,
                        configurationVersion,
                        interpreted.rawTimestamp()));
            }
            if (interpreted.status() == CheckTimeV2ResultStatus.REMOTE_OPERATION_REPORTED_FAILURE) {
                return ServerTimeClientResult.failure(ServerTimeFailureCode.REMOTE_OPERATION_REPORTED_FAILURE, interpreted.diagnostic());
            }
            return ServerTimeClientResult.failure(ServerTimeFailureCode.INVALID_RESPONSE, interpreted.diagnostic());
        } catch (HttpTimeoutException ex) {
            return ServerTimeClientResult.failure(ServerTimeFailureCode.TIMEOUT, "HTTP request timed out");
        } catch (IOException ex) {
            return ServerTimeClientResult.failure(ServerTimeFailureCode.NETWORK_ERROR, sanitize(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ServerTimeClientResult.failure(ServerTimeFailureCode.NETWORK_ERROR, "HTTP request was interrupted");
        }
    }

    private static String sanitize(Exception exception) {
        return exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
    }
}