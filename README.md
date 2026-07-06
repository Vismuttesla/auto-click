# precision-trigger

A Java 21 + JavaFX desktop application for estimating server time from the `CheckTimeV2` endpoint, maintaining a monotonic server-clock model, and preparing auditable precision-trigger execution plans.

## Run

Use Maven:

```bash
mvn javafx:run
```

## Build and Test

```bash
mvn test
mvn package
```

## Current behavior

- Uses a reusable Java 21 `HttpClient`
- Accepts runtime Bearer token input from the JavaFX UI
- Parses the observed `CheckTimeV2` production response shape
- Converts decimal epoch milliseconds with `BigDecimal`
- Prevents overlapping sync requests by policy
- Persists runtime settings in `config/runtime-settings.json`
- Writes append-only daily UTC JSONL audit events in `logs/`
- Keeps authentication audit metadata safe by excluding raw bearer tokens and authorization headers

## Runtime settings precedence

Recommended precedence is:

1. Secure runtime or environment secrets
2. `config/runtime-settings.json`
3. `src/main/resources/config/application.yaml`

`config/runtime-settings.json` is reserved for user-editable runtime values such as sync interval and display zone. The raw bearer token must not be stored there. The default token persistence policy is `MEMORY_ONLY`.

## JSONL logging

- Files are written as `logs/precision-trigger-YYYY-MM-DD.jsonl`
- Each physical line is one complete JSON object
- Events include `eventId`, `eventType`, `priority`, UTC `timestamp`, and explicit metadata
- Authentication audit events persist safe metadata like `tokenVersion`, `source`, and optional `jwtExpiresAt`

## Timing limitations

This application is best-effort precision software on a general-purpose JVM and operating system. It does not claim hard real-time guarantees. One-way latency cannot be known exactly from a normal HTTP RTT without additional trusted timing information or explicit assumptions. JSONL disk writes are intentionally moved onto a dedicated async writer thread so the precision path does not block on disk I/O.
