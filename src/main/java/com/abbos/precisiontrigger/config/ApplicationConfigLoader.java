package com.abbos.precisiontrigger.config;

import com.abbos.precisiontrigger.time.TimestampFormat;
import com.abbos.precisiontrigger.time.TimestampSemantics;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

public final class ApplicationConfigLoader {
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ApplicationConfigLoader() {
        yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.registerModule(new JavaTimeModule());
        yamlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public LoadedConfiguration load() throws IOException {
        RawRoot root;
        try (InputStream stream = ApplicationConfigLoader.class.getResourceAsStream("/config/application.yaml")) {
            if (stream == null) {
                throw new IOException("Missing /config/application.yaml");
            }
            root = yamlMapper.readValue(stream, RawRoot.class);
        }

        ApplicationConfig applicationConfig = new ApplicationConfig(
                new ServerConfig(
                        root.server.timeUrl,
                        parseDuration(root.http.connectTimeout, Duration.ofSeconds(3)),
                        parseDuration(root.http.requestTimeout, Duration.ofSeconds(2)),
                        new ServerConfig.TimestampConfig(
                                root.server.timestamp.jsonPointer,
                                root.server.timestamp.format,
                                root.server.timestamp.semantics),
                        new ServerConfig.ResponseContractConfig(
                                root.server.response.successJsonPointer,
                                root.server.response.errorJsonPointer,
                                root.server.response.expectedSuccessValue)),
                new RequestHeadersConfig(root.request.accept, root.request.contentType, root.request.userAgent),
                new TimingConfig(
                        parseDuration(root.timing.syncInterval, Duration.ofSeconds(60)),
                        parseDuration(root.timing.minSyncInterval, Duration.ofSeconds(5)),
                        parseDuration(root.timing.maxSyncInterval, Duration.ofHours(1)),
                        root.timing.sampleWindowSize,
                        parseDuration(root.timing.maxClockAge, Duration.ofMinutes(3)),
                        root.timing.minimumConfidence,
                        parseDuration(root.timing.finalFreezeWindow, Duration.ofSeconds(2)),
                        parseDuration(root.timing.coarseThreshold, Duration.ofMillis(50)),
                        parseDuration(root.timing.spinThreshold, Duration.ofMillis(2)),
                        parseDuration(root.timing.defaultActionOverhead, Duration.ZERO)),
                new AuthConfig(
                        parseDuration(root.auth.expiringSoonThreshold, Duration.ofMinutes(5)),
                        parseDuration(root.auth.unauthorizedBackoff, Duration.ofSeconds(30))),
                new LoggingConfig(Path.of(root.logging.directory), root.logging.asyncQueueCapacity, root.logging.indexOnStartup),
                new UiConfig(root.ui.zoneId)
        );

        RuntimeSettings defaults = new RuntimeSettings(
                applicationConfig.timing().syncInterval(),
                "system".equalsIgnoreCase(applicationConfig.ui().zoneId()) ? RuntimeSettings.DEFAULT.displayZoneId() : applicationConfig.ui().zoneId(),
                RuntimeSettings.DEFAULT.configurationVersion(),
                RuntimeSettings.DEFAULT.tokenPersistencePolicy()
        );
        return new LoadedConfiguration(applicationConfig, defaults, jsonMapper);
    }

    private static Duration parseDuration(String raw, Duration fallback) {
        return raw == null || raw.isBlank() ? fallback : Duration.parse(raw);
    }

    public record LoadedConfiguration(ApplicationConfig applicationConfig,
                                      RuntimeSettings runtimeDefaults,
                                      ObjectMapper jsonMapper) {
    }

    @SuppressWarnings("unused")
    private static final class RawRoot {
        public RawServer server = new RawServer();
        public RawRequest request = new RawRequest();
        public RawHttp http = new RawHttp();
        public RawTiming timing = new RawTiming();
        public RawAuth auth = new RawAuth();
        public RawLogging logging = new RawLogging();
        public RawUi ui = new RawUi();
    }

    private static final class RawServer {
        public String timeUrl;
        public RawTimestamp timestamp = new RawTimestamp();
        public RawResponse response = new RawResponse();
    }

    private static final class RawTimestamp {
        public String jsonPointer;
        public TimestampFormat format = TimestampFormat.EPOCH_MILLIS_DECIMAL;
        public TimestampSemantics semantics = TimestampSemantics.UNKNOWN;
    }

    private static final class RawResponse {
        public String successJsonPointer;
        public String errorJsonPointer;
        public String expectedSuccessValue;
    }

    private static final class RawRequest {
        public String accept;
        public String contentType;
        public String userAgent;
    }

    private static final class RawHttp {
        public String connectTimeout;
        public String requestTimeout;
    }

    private static final class RawTiming {
        public String syncInterval;
        public String minSyncInterval;
        public String maxSyncInterval;
        public int sampleWindowSize = 30;
        public String maxClockAge;
        public double minimumConfidence = 0.70;
        public String finalFreezeWindow;
        public String coarseThreshold;
        public String spinThreshold;
        public String defaultActionOverhead;
    }

    private static final class RawAuth {
        public String expiringSoonThreshold;
        public String unauthorizedBackoff;
    }

    private static final class RawLogging {
        public String directory = "logs";
        public int asyncQueueCapacity = 10000;
        public boolean indexOnStartup = true;
    }

    private static final class RawUi {
        public String zoneId = "system";
    }
}
