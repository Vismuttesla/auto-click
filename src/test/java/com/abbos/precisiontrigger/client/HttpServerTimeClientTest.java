package com.abbos.precisiontrigger.client;

import com.abbos.precisiontrigger.auth.JwtMetadataInspector;
import com.abbos.precisiontrigger.auth.RuntimeAuthTokenService;
import com.abbos.precisiontrigger.checktime.CheckTimeV2ResponseInterpreter;
import com.abbos.precisiontrigger.clock.SystemNanoClock;
import com.abbos.precisiontrigger.clock.SystemWallClock;
import com.abbos.precisiontrigger.config.AuthConfig;
import com.abbos.precisiontrigger.config.RequestHeadersConfig;
import com.abbos.precisiontrigger.config.ServerConfig;
import com.abbos.precisiontrigger.logging.JsonlEventLogger;
import com.abbos.precisiontrigger.network.DefaultAuthorizationHeaderProvider;
import com.abbos.precisiontrigger.time.TimestampFormat;
import com.abbos.precisiontrigger.time.TimestampSemantics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServerTimeClientTest {
    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesObservedProductionPayloadSuccessfully() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/Cabinet/CheckTimeV2", exchange -> respond(exchange, 200, "{\"error\":null,\"success\":\"true\",\"data\":1783169575987.4}"));
        server.start();

        HttpServerTimeClient client = newClient(server.getAddress().getPort());
        ServerTimeClientResult result = client.fetchServerTime(1L, 1L);

        assertThat(result.success()).isTrue();
        assertThat(result.measurement().serverTimestamp()).isEqualTo(Instant.ofEpochSecond(1783169575L, 987_400_000L));
    }

    @Test
    void mapsUnauthorizedResponseExplicitly() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/Cabinet/CheckTimeV2", exchange -> respond(exchange, 401, "{\"error\":null,\"success\":\"false\",\"data\":0}"));
        server.start();

        HttpServerTimeClient client = newClient(server.getAddress().getPort());
        ServerTimeClientResult result = client.fetchServerTime(1L, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo(ServerTimeFailureCode.UNAUTHORIZED);
    }

    private HttpServerTimeClient newClient(int port) {
        ObjectMapper objectMapper = new ObjectMapper();
        RuntimeAuthTokenService authTokenService = new RuntimeAuthTokenService(
                new JwtMetadataInspector(objectMapper),
                new JsonlEventLogger(tempDir),
                new AuthConfig(Duration.ofMinutes(5), Duration.ofSeconds(30)));
        authTokenService.applyToken(jwtToken(Instant.now().plus(Duration.ofHours(1))), Instant.now());
        ServerConfig serverConfig = new ServerConfig(
                "http://127.0.0.1:" + port + "/api/Cabinet/CheckTimeV2",
                Duration.ofSeconds(3),
                Duration.ofSeconds(3),
                new ServerConfig.TimestampConfig("/data", TimestampFormat.EPOCH_MILLIS_DECIMAL, TimestampSemantics.UNKNOWN),
                new ServerConfig.ResponseContractConfig("/success", "/error", "true"));
        CheckTimeV2ResponseInterpreter interpreter = HttpServerTimeClient.defaultInterpreter(objectMapper, serverConfig);
        return new HttpServerTimeClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                serverConfig,
                new RequestHeadersConfig("application/json", "application/json", "precision-trigger-test"),
                new DefaultAuthorizationHeaderProvider(authTokenService),
                authTokenService,
                interpreter,
                new SystemNanoClock(),
                new SystemWallClock(),
                new JsonlEventLogger(tempDir));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.sendResponseHeaders(status, body.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body.getBytes());
        }
        exchange.close();
    }

    private static String jwtToken(Instant exp) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + exp.getEpochSecond() + "}").getBytes());
        return header + "." + payload + ".signature";
    }
}