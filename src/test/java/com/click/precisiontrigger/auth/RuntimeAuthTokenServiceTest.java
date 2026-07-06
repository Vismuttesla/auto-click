package com.click.precisiontrigger.auth;

import com.click.precisiontrigger.config.AuthConfig;
import com.click.precisiontrigger.logging.JsonlEventLogger;
import com.click.precisiontrigger.network.DefaultAuthorizationHeaderProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeAuthTokenServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesOptionalBearerPrefix() {
        assertThat(RuntimeAuthTokenService.normalize("  Bearer abc.def.ghi  ")).isEqualTo("abc.def.ghi");
        assertThat(RuntimeAuthTokenService.normalize("plain-token")).isEqualTo("plain-token");
    }

    @Test
    void rejectsBlankToken() {
        RuntimeAuthTokenService service = newService();

        assertThatThrownBy(() -> service.applyToken("   ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void publishesLatestTokenAndSafeStatusOnly() {
        RuntimeAuthTokenService service = newService();
        Instant now = Instant.parse("2026-07-06T00:00:00Z");
        service.applyToken(jwtToken(Instant.parse("2026-07-06T01:00:00Z")), now);

        assertThat(service.currentBearerToken()).isPresent();
        assertThat(new DefaultAuthorizationHeaderProvider(service).authorizationHeader()).contains("Bearer " + service.currentBearerToken().orElseThrow());
        assertThat(service.snapshot().present()).isTrue();
        assertThat(service.snapshot().jwtSubject()).isEqualTo("abbos");
        assertThat(service.status().tokenPresent()).isTrue();
        assertThat(service.status().safeLastFailureMessage()).isNull();
    }

    @Test
    void marksUnauthorizedAndRespectsBackoffWindow() {
        RuntimeAuthTokenService service = newService();
        Instant now = Instant.parse("2026-07-06T00:00:00Z");
        service.applyToken(jwtToken(Instant.parse("2026-07-06T01:00:00Z")), now);

        service.markUnauthorized(now.plusSeconds(5), "401 Unauthorized");

        assertThat(service.status().state()).isEqualTo(AuthTokenState.AUTH_REJECTED);
        assertThat(service.canIssueAuthenticatedRequest(now.plusSeconds(10))).isFalse();
        assertThat(service.canIssueAuthenticatedRequest(now.plusSeconds(40))).isTrue();
    }

    @Test
    void clearTokenRemovesFutureAuthorizationUse() {
        RuntimeAuthTokenService service = newService();
        Instant now = Instant.parse("2026-07-06T00:00:00Z");
        service.applyToken(jwtToken(Instant.parse("2026-07-06T01:00:00Z")), now);

        service.clearToken(now.plusSeconds(10));

        assertThat(service.currentBearerToken()).isEmpty();
        assertThat(service.status().state()).isEqualTo(AuthTokenState.NO_TOKEN);
        assertThat(new DefaultAuthorizationHeaderProvider(service).authorizationHeader()).isEmpty();
    }

    @Test
    void canBeMarkedAuthenticatedAfterSuccessfulTest() {
        RuntimeAuthTokenService service = newService();
        Instant now = Instant.parse("2026-07-06T00:00:00Z");
        service.applyToken(jwtToken(Instant.parse("2026-07-06T01:00:00Z")), now);

        service.markAuthenticated(now.plusSeconds(1));

        assertThat(service.status().state()).isEqualTo(AuthTokenState.AUTHENTICATED);
    }

    private RuntimeAuthTokenService newService() {
        return new RuntimeAuthTokenService(
                new JwtMetadataInspector(new ObjectMapper()),
                new JsonlEventLogger(tempDir),
                new AuthConfig(Duration.ofMinutes(5), Duration.ofSeconds(30))
        );
    }

    private static String jwtToken(Instant exp) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + exp.getEpochSecond() + ",\"sub\":\"abbos\"}").getBytes());
        return header + "." + payload + ".signature";
    }
}