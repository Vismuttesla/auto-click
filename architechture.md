# precision-trigger Architecture Specification

You are a principal-level Java software engineer with 15+ years of
production experience in:

- Java 21
- JVM concurrency
- low-latency systems
- distributed systems
- network timing
- monotonic clocks
- precision scheduling
- JavaFX desktop applications
- production-grade HTTP clients
- clean architecture
- observability
- append-only event logs
- file-backed search indexing
- deterministic testing
- race-condition analysis

Your task is to CREATE A COMPLETE APPLICATION FROM SCRATCH.

This is not a toy example.
This is not a tutorial snippet.
This is not a one-class demo.

Build a complete, compilable, testable Java 21 desktop application.

The application name is:

precision-trigger

Base package:

com.abbos.precisiontrigger

Use:

- Java 21
- Maven
- JavaFX 21
- Java 21 HttpClient
- Jackson
- SLF4J
- Logback
- JUnit 5
- AssertJ
- Awaitility where useful

Do not use Spring Boot for the core timing engine.

The final application must:

- compile
- start
- show the JavaFX UI
- run tests successfully
- contain no hardcoded bearer token
- contain no fake undocumented production endpoint
- use clean architecture boundaries
- use immutable records where appropriate
- provide deterministic tests

==================================================
0. CRITICAL ENGINEERING PRINCIPLES
==================================================

Follow these rules without exception:

1. Do not implement a naive countdown timer.

2. Do not use System.currentTimeMillis() as the primary precision
   elapsed-time clock.

3. Use System.nanoTime() through an injectable NanoClock abstraction
   for monotonic scheduling.

4. Do not decrement remaining time manually.

Forbidden examples:

remainingMillis--;

timer = timer - 1;

5. Do not create a new HttpClient per request.

6. Do not hardcode bearer tokens.

7. Do not claim that S1 and S2 can always be independently measured
   exactly from one normal HTTP round trip.

8. Do not invent the final business action API endpoint.

9. Do not put all logic into JavaFX controllers.

10. Do not perform blocking disk I/O on the precision scheduling thread.

11. Do not allow duplicate primary action execution.

12. Do not silently use stale clock models.

13. Do not mutate published ExecutionPlan objects.

14. Do not silently mix:
    - epoch milliseconds
    - epoch seconds
    - monotonic nanoseconds
    - Duration
    - Instant

15. Every time unit must be explicit.

16. All important timing formulas must have tests.

17. The code must model uncertainty honestly.

18. This is best-effort precision on a general-purpose JVM/OS.
    Do not claim hard real-time guarantees.

==================================================
1. BUSINESS GOAL
==================================================

The application has a predefined target server time called:

PLAY_TIME

PLAY_TIME means:

The server-side target time when the resulting action request should
arrive at the server as close as practically possible.

Example:

2026-07-05T12:00:00.000Z

The application synchronizes with a server time API.

Default endpoint:

GET
http://172.16.14.19:5001/api/Cabinet/CheckTimeV2

The endpoint must be externally configurable.

The default synchronization interval is:

60 seconds

However, the user must be able to change the interval at runtime from
the application UI without restarting the application.

Examples:

60 seconds
30 seconds
10 seconds
120 seconds

The application maintains:

S1 = estimated client-to-server outbound latency

S2 = estimated server-to-client inbound latency

The conceptual action timing formula is:

desiredFireServerTime
    =
PLAY_TIME
    -
estimatedS1
    -
estimatedClientExecutionOverhead

The resulting desired server-side fire time must be converted to an
absolute local monotonic deadline.

==================================================
2. PROJECT CREATION
==================================================

Create a complete Maven project from scratch.

Create:

pom.xml

README.md

config/application.yaml

config/runtime-settings.json

logs/.gitkeep

src/main/java

src/main/resources

src/test/java

Base package:

com.abbos.precisiontrigger

Configure Maven for:

Java 21

JavaFX 21

JUnit 5

Required dependencies should include:

- JavaFX controls
- JavaFX FXML
- Jackson databind
- Jackson Java Time module
- Jackson YAML support if used for configuration
- SLF4J API
- Logback classic
- JUnit Jupiter
- AssertJ
- Awaitility where justified

Configure:

maven-compiler-plugin

maven-surefire-plugin

JavaFX Maven plugin

The application must launch through a clear documented Maven command.

==================================================
3. APPLICATION ARCHITECTURE
==================================================

Implement these architectural areas:

app/
config/
clock/
time/
sync/
latency/
client/
planning/
scheduler/
action/
execution/
logging/
logsearch/
network/
ui/

Core timing code must not depend on JavaFX.

JavaFX is an adapter layer.

Core timing code must be testable without opening a UI.

==================================================
4. CLOCK ABSTRACTIONS
==================================================

Create:

public interface NanoClock {
    long nanoTime();
}

Production implementation:

SystemNanoClock

using:

System.nanoTime()

Create:

public interface WallClock {
    Instant now();
}

Production implementation:

SystemWallClock

using:

Clock.systemUTC()

Tests must use controllable fake clocks.

Create:

FakeNanoClock

Do not make timing tests depend primarily on real sleeping.

==================================================
5. SERVER TIME API
==================================================

Create:

ServerTimeClient

Implementation:

HttpServerTimeClient

Default endpoint:

http://172.16.14.19:5001/api/Cabinet/CheckTimeV2

Configuration example:

server:
  time-url: "http://172.16.14.19:5001/api/Cabinet/CheckTimeV2"

Use one reusable Java 21 HttpClient.

Requirements:

- configurable connect timeout
- configurable request timeout
- connection reuse
- no new client per request
- structured error mapping
- authentication abstraction

Create:

AuthTokenProvider

Never hardcode a real bearer token.

Allow implementations such as:

EnvironmentAuthTokenProvider

RuntimeAuthTokenProvider

Do not log bearer tokens.

==================================================
6. SERVER TIME RESPONSE PARSING
==================================================

The real CheckTimeV2 production response shape is now known.

Do not model `success` as a boolean or `data` as a long-only field.

Create:

public interface ServerTimeResponseParser

Create:

JacksonServerTimeResponseParser

Make the timestamp field configurable and preserve the observed production shape.

Example configuration:

server:
  timestamp:
    json-pointer: "/data"
    format: "EPOCH_MILLIS_DECIMAL"
    semantics: "UNKNOWN"
  response:
    success-json-pointer: "/success"
    error-json-pointer: "/error"
    expected-success-value: "true"

Support:

EPOCH_MILLIS

EPOCH_MILLIS_DECIMAL

EPOCH_SECONDS

ISO_8601

Create:

TimestampFormat

Create:

ParsedServerTime

If parsing fails:

- return explicit failure
- log TIME_SYNC_FAILED
- do not update ServerClockModel with invalid data

Observed production example:

{
  "error": null,
  "success": "true",
  "data": 1783169575987.4
}

==================================================
7. TIMESTAMP SEMANTICS
==================================================

Create:

enum TimestampSemantics {
    REQUEST_RECEIVED_AT,
    RESPONSE_CREATED_AT,
    RESPONSE_SENT_AT,
    UNKNOWN
}

The meaning of the server timestamp is still unresolved.

Do not silently assume semantics.

Configuration example:

server:
  timestamp:
    semantics: "UNKNOWN"

If UNKNOWN:

- lower confidence
- emit warning
- never claim exact synchronization

The production parser contract is resolved; the semantic meaning of `/data`
is not. Keep the default semantics as `UNKNOWN` until the server-side moment
is explicitly documented.

If the timestamp corresponds to response creation/send time:

estimatedServerTimeAtClientReceive
    â‰ˆ
serverTimestamp
    +
estimatedS2

Model this explicitly.

==================================================
8. TIME SYNC MEASUREMENT
==================================================

Create:

TimeMeasurement

For every synchronization attempt:

1. Capture monotonic send marker as close as practically possible to
   request dispatch:

localSendNano = nanoClock.nanoTime()

2. Send CheckTimeV2 request.

3. When the HTTP response becomes available, capture:

localReceiveNano = nanoClock.nanoTime()

Capture this before:

- expensive JSON parsing
- UI work
- unrelated logging
- unrelated allocations

4. Parse server timestamp.

5. Create TimeMeasurement.

The measurement must preserve:

- localSendNano
- localReceiveNano
- serverTimestamp
- wall-clock collection time
- HTTP status
- request sequence
- active runtime configuration version

==================================================
9. RUNTIME-CONFIGURABLE SYNC INTERVAL
==================================================

Default:

60 seconds

The value must be changeable at runtime from the application UI.

Create:

SyncIntervalService

Create:

SyncIntervalController

Create:

DynamicSyncScheduler

Represent intervals as:

java.time.Duration

Never use ambiguous raw numeric interval values.

Bad:

long interval = 60;

Good:

Duration.ofSeconds(60);

The UI must support:

- numeric interval input
- time unit selection
- Apply Interval
- Sync Now
- current active interval
- last sync
- next planned sync
- sync status

Supported UI units may include:

SECONDS

MINUTES

==================================================
10. SYNC INTERVAL VALIDATION
==================================================

Default values:

default interval:
60 seconds

minimum:
5 seconds

maximum:
1 hour

These bounds must be configurable.

Reject:

- zero
- negative
- malformed input
- below minimum
- above maximum

Do not silently clamp.

Return clear validation errors.

==================================================
11. DYNAMIC RESCHEDULING
==================================================

When the user changes:

60 seconds

to:

30 seconds

the application must:

1. validate
2. persist runtime setting
3. increment configuration version
4. atomically publish the setting
5. safely replace old periodic scheduling
6. avoid duplicate schedulers
7. avoid overlapping synchronization requests
8. update next planned sync
9. emit audit event
10. update UI

The old 60-second periodic task must not continue running in parallel
with the new 30-second schedule.

==================================================
12. SYNC SCHEDULING MODE
==================================================

Create:

enum SyncSchedulingMode {
    FIXED_DELAY,
    FIXED_RATE
}

Default:

FIXED_DELAY

Document behavior.

Default normal synchronization should avoid schedule compression after
slow requests.

==================================================
13. SYNC OVERLAP POLICY
==================================================

Create:

enum SyncOverlapPolicy {
    SKIP_IF_PREVIOUS_RUNNING,
    DELAY_UNTIL_PREVIOUS_COMPLETES
}

Default:

SKIP_IF_PREVIOUS_RUNNING

Do not allow accidental parallel CheckTimeV2 measurements.

Example:

interval = 10 sec

request duration = 15 sec

Do not create:

sync #1 running
sync #2 running
sync #3 running

Log skipped synchronization attempts.

==================================================
14. MANUAL SYNC NOW
==================================================

The UI must provide:

Sync Now

Manual sync must execute the same production pipeline as periodic sync.

Do not create a simplified separate algorithm.

Pipeline:

ServerTimeClient
    ->
TimeMeasurement
    ->
S1/S2 strategy
    ->
LatencySample
    ->
JSONL logging
    ->
SampleWindow
    ->
LatencyEstimator
    ->
ServerClockModel
    ->
ExecutionPlan recalculation

If another sync is already running:

default behavior:
do not start overlapping request

Return UI status:

ALREADY_RUNNING

Optionally allow one coalesced pending request, but keep behavior explicit.

==================================================
15. S1 / S2 STRATEGY ARCHITECTURE
==================================================

Create:

public interface S1S2EstimationStrategy

Method concept:

LatencyEstimate estimate(
    TimeMeasurement measurement,
    SampleWindowSnapshot history
);

Do not hardcode one universal assumption.

Implement:

1. ProvidedS1S2Strategy

This is intended for a trusted existing S1/S2 measurement algorithm
that may be integrated later.

Do not invent missing business logic.

2. SymmetricRttFallbackStrategy

Fallback only:

S1 â‰ˆ effectiveRTT / 2

S2 â‰ˆ effectiveRTT / 2

Mark this as an assumption.

Lower confidence accordingly.

3. HistoricalCalibratedStrategy

Use historical:
- stable samples
- jitter
- outlier filtering
- low-latency subset
- smoothing where justified

The design must allow a future:

NtpLikeFourTimestampStrategy

if sufficient server timestamps become available.

Document prominently:

One-way latency cannot be known exactly from an ordinary HTTP RTT
measurement without assumptions or additional timing information.

... [truncated for brevity in this patch example] ...
Create:

LatencySample

Fields should include:

- long sequence
- Instant localCollectedAt
- Instant serverTimestamp
- Instant estimatedServerTimeAtReceive
- long localSendNano
- long localReceiveNano
- Duration s1
- Duration s2
- Duration rtt
- Duration jitter
- String estimationStrategy
- double qualityScore
- double confidence
- boolean accepted
- String rejectionReason
- Duration activeSyncInterval
- long configurationVersion

Use Duration.

Never mix units implicitly.

==================================================
17. SAMPLE WINDOW
==================================================

Create bounded:

SampleWindow

Default size:

30 samples

Create immutable:

SampleWindowSnapshot

The window must:

- be thread-safe
- remain bounded
- avoid unbounded memory growth
- expose immutable snapshots

==================================================
18. OUTLIER FILTERING
==================================================

Create:

OutlierFilter

Implementation:

MadOutlierFilter

Use a robust approach such as:

median
median absolute deviation

Handle:

- empty history
- one sample
- insufficient sample count
- zero MAD
- extreme spikes

Do not reject values blindly.

Persist rejected samples to audit log.

They should remain historically visible.

==================================================
19. JITTER ESTIMATION
==================================================

Create:

JitterEstimator

Calculate jitter explicitly.

Document chosen definition.

Examples may include:

variation between consecutive effective latency samples

or

robust dispersion over a recent window

Keep the implementation testable.

==================================================
20. SAMPLE QUALITY
==================================================

Create:

SampleQualityScorer

Quality may account for:

- RTT relative to recent baseline
- jitter
- timestamp semantics certainty
- strategy reliability
- outlier state
- sample freshness

Return score:

0.0 to 1.0

Do not pretend this is mathematically exact unless justified.

Document the heuristic.

==================================================
21. ROBUST LATENCY ESTIMATOR
==================================================

Create:

LatencyEstimator

Implementation:

RobustLatencyEstimator

Responsibilities:

- validate samples
- reject impossible negative values
- reject timeout measurements
- detect spikes
- calculate jitter
- choose trustworthy samples
- avoid blindly trusting latest sample
- estimate trusted S1
- estimate trusted S2
- expose confidence

A reasonable design may use:

- median
- MAD
- lowest-latency subset
- EWMA

But keep each technique explicit.

Do not create opaque magic formulas.

==================================================
22. SERVER CLOCK MODEL
==================================================

Create:

public interface ServerClock {
    Instant now();
    ServerClockSnapshot snapshot();
}

Create:

ServerClockModel

Use immutable snapshot:

ServerClockSnapshot

Fields:

- Instant estimatedServerTimeAtAnchor
- long localMonotonicAnchorNano
- Duration estimatedS1
- Duration estimatedS2
- Duration jitter
- double confidence
- long sourceSampleSequence
- long version
- Instant createdAt

Publish using:

AtomicReference<ServerClockSnapshot>

Server time progression:

elapsedNanos
    =
nanoClock.nanoTime()
    -
localMonotonicAnchorNano

serverNow
    =
estimatedServerTimeAtAnchor
    +
elapsedNanos

Avoid broad synchronized blocks.

==================================================
23. STALE CLOCK POLICY
==================================================

Create explicit stale-clock rules.

Configuration example:

timing:
  max-clock-age: 3m
  minimum-confidence: 0.70

If:

clock age > maximum allowed

or

confidence < required threshold

then:

- mark precision readiness false
- show warning in UI
- prevent normal precision arming by default

Do not silently continue with dangerous stale data.

==================================================
24. PLAY_TIME MODEL
==================================================

Create:

TargetTimeService

Internally represent target as:

Instant

The JavaFX UI should allow:

- target date
- target time including milliseconds
- display time zone

Internally convert to Instant.

Do not use LocalDateTime without explicit ZoneId conversion.

Default display zone may be system zone, but make it visible and
configurable.

Validate:

- target exists
- target is in future when arming
- target conversion is unambiguous

==================================================
25. EXECUTION PLAN
==================================================

Create immutable:

ExecutionPlan

Fields:

- Instant targetServerTime
- Instant desiredFireServerTime
- long localDeadlineNano
- Duration selectedS1
- Duration selectedS2
- Duration executionOverhead
- Duration jitter
- double confidence
- long sourceSampleSequence
- long clockVersion
- long planVersion
- long configurationVersion
- Instant createdAt

Formula:

desiredFireServerTime
    =
PLAY_TIME
    -
selectedS1
    -
estimatedClientExecutionOverhead

Then convert:

desiredFireServerTime

into:

absolute local monotonic deadline

using the current ServerClockSnapshot.

Publish:

AtomicReference<ExecutionPlan>

Never mutate published plans.
==================================================
26. ACTION EXECUTION OVERHEAD
==================================================

Create:

ActionOverheadEstimator

Keep:

network S1

separate from:

client execution overhead

Potential client overhead:

- callback dispatch
- task handoff
- serialization
- HTTP queue
- request preparation
- socket dispatch preparation

Create initial implementation:

FixedActionOverheadEstimator

with configurable Duration.

Default may be:

PT0S

Do not invent measured overhead.

The architecture must allow future observed calibration.

==================================================
27. PRECISION SCHEDULER
==================================================

Create:

public interface PrecisionScheduler

Implement:

HybridPrecisionScheduler

The final timing loop must use a dedicated platform thread.

Do not use a virtual thread for the final precision waiting loop.

Use absolute monotonic deadline.

Three stages:

Stage 1:
far from deadline
-> coarse LockSupport.parkNanos

Stage 2:
near deadline
-> short parkNanos intervals

Stage 3:
final precision window
-> Thread.onSpinWait

Configurable defaults:

coarse threshold:
50 ms

spin threshold:
2 ms

Do not busy-spin for long periods.

Do not rely on one long Thread.sleep.

Record:

- planned deadline nano
- actual fire nano
- scheduler error nano

==================================================
28. FINAL FREEZE WINDOW
==================================================

Create configurable final freeze window.

Default:

2 seconds

Before freeze:

- trusted sync may update clock
- trusted sync may update execution plan

Inside freeze:

- freeze selected plan
- avoid unnecessary periodic synchronization
- avoid plan churn
- minimize allocations
- minimize UI work
- avoid search work
- no disk I/O on precision thread

State transition:

ARMED
    ->
FINALIZING
    ->
FIRING

If user changes sync interval during FINALIZING:

- accept valid setting
- persist it
- increment config version
- apply to future scheduling according to policy
- do not destabilize frozen current target plan
- do not introduce unnecessary sync work into critical final window

==================================================
29. APPLICATION STATE MACHINE
==================================================

Create explicit states:

IDLE
SYNCING
READY
ARMED
FINALIZING
FIRING
WAITING_ACK
CONFIRMED
FAILED
CANCELLED

Do not model lifecycle using unrelated booleans.

Create:

ApplicationStateMachine

Validate transitions.

Examples:

IDLE -> SYNCING
SYNCING -> READY
READY -> ARMED
ARMED -> FINALIZING
FINALIZING -> FIRING
FIRING -> WAITING_ACK
WAITING_ACK -> CONFIRMED

Allow explicit error transitions.

Reject invalid transitions.

Test them.

==================================================
30. ACTION EXECUTOR
==================================================

Create:

public interface ActionExecutor

Implement:

HttpActionExecutor

UiButtonActionExecutor

DryRunActionExecutor

Important:

The real final action API endpoint is NOT specified by this
specification.

Do not invent one.

Keep HTTP action URL externally configurable.

DryRunActionExecutor must allow full scheduler testing without performing
a real business action.

UiButtonActionExecutor should remain an adapter and must not contain
core timing logic.

==================================================
31. ONE-SHOT GUARANTEE
==================================================

Create:

OneShotGuard

Use atomic compare-and-set semantics.

Guarantee:

one target execution can have only one primary trigger.

Use:

AtomicBoolean

or equivalent atomic state machine.

Test simultaneous racing trigger attempts.

==================================================
32. ACTION OUTCOME MODEL
==================================================

Create explicit outcome states such as:

NOT_SENT
SENT
ACKNOWLEDGED
REJECTED
FAILED_BEFORE_SEND
AMBIGUOUS_TIMEOUT

Do not treat:

request timeout

as automatically meaning:

request was not received

Differentiate ambiguous outcomes.

==================================================
33. RETRY POLICY
==================================================

Do not blindly retry timing-sensitive actions.

If idempotency keys are available:

- use one operation ID
- reuse for safe retry

If idempotency is unavailable:

- default to no blind duplicate retry
- record ambiguous outcome

Create explicit:

RetryPolicy

Default should prioritize duplicate prevention.

==================================================
34. CONNECTION PRE-WARMING
==================================================

Avoid first-use initialization at the critical moment.

Before target where appropriate:

- initialize HTTP client
- initialize serializer
- initialize relevant execution classes
- optionally perform safe connection pre-warming if supported

Do not open a brand-new HttpClient at fire time.

==================================================
35. JSONL EVENT LOGGING
==================================================

Persist timing and execution history to human-readable append-only JSONL
files.

Do not use a database for this requirement.

Directory:

logs/

Default file rotation:

one UTC file per day

Example:

logs/precision-trigger-2026-07-05.jsonl

Each physical line must contain one complete JSON object.

Files must be:

- human readable
- text editor friendly
- machine parseable
- persistent across restarts
- searchable from inside the application
==================================================
36. REQUIRED LOG EVENT TYPES
==================================================

Create enum:

LogEventType

Include at least:

APPLICATION_STARTED
APPLICATION_STOPPING

TIME_SYNC_STARTED
TIME_SYNC_SAMPLE
TIME_SYNC_FAILED

LATENCY_ESTIMATE_UPDATED
SERVER_CLOCK_UPDATED

SYNC_INTERVAL_CHANGE_REQUESTED
SYNC_INTERVAL_CHANGED
SYNC_INTERVAL_CHANGE_REJECTED

MANUAL_SYNC_REQUESTED
MANUAL_SYNC_STARTED
MANUAL_SYNC_COMPLETED
MANUAL_SYNC_SKIPPED

PERIODIC_SYNC_SKIPPED

EXECUTION_PLAN_UPDATED
FINAL_WINDOW_ENTERED

ACTION_FIRE_STARTED
ACTION_FIRED
ACTION_ACKNOWLEDGED
ACTION_REJECTED
ACTION_FAILED
ACTION_AMBIGUOUS

NETWORK_ENVIRONMENT_CHANGED

==================================================
37. TIME SYNC SAMPLE LOG FIELDS
==================================================

Every TIME_SYNC_SAMPLE must persist:

- eventId
- eventType
- timestamp
- sequence
- localCollectedAt
- serverTimestamp
- estimatedServerTimeAtReceive
- localSendNano where useful
- localReceiveNano where useful
- s1Nanos
- s2Nanos
- rttNanos
- jitterNanos
- estimationStrategy
- qualityScore
- confidence
- accepted
- rejectionReason
- activeSyncIntervalNanos
- configurationVersion
- targetServerTime if present
- timeUntilTargetNanos if applicable
- clockVersion when available
- planVersion when available

Use explicit units in canonical field names.

==================================================
38. EXECUTION AUDIT LOG
==================================================

For every final action persist:

- targetServerTime
- selectedS1Nanos
- selectedS2Nanos
- sourceSampleSequence
- sourceClockVersion
- sourcePlanVersion
- configurationVersion
- executionOverheadNanos
- desiredFireServerTime
- plannedLocalDeadlineNano
- actualFireNano
- schedulerErrorNanos
- actionOutcome
- acknowledgment information
- ambiguous outcome information

The audit must answer:

Which S1/S2 values were used?

Which sample produced them?

What confidence existed?

Which execution plan was used?

What was planned?

What actually happened?

How early or late did the local scheduler fire?

==================================================
39. ASYNC LOG WRITER
==================================================

The precision scheduling thread must never perform blocking disk I/O.

Create:

StructuredLogService

AsyncJsonlLogWriter

LogEventSerializer

LogRotationPolicy

Architecture:

producer
    ->
bounded queue
    ->
dedicated log writer thread
    ->
JSONL append
    ->
index update

Never call from precision thread:

Files.write
FileWriter.flush
fsync

Define queue overflow behavior.

Expose dropped event metrics.

==================================================
40. LOG PRIORITY
==================================================

Create:

enum LogPriority {
    NORMAL,
    CRITICAL
}

Critical examples:

ACTION_FIRED
ACTION_ACKNOWLEDGED
ACTION_REJECTED
ACTION_FAILED
ACTION_AMBIGUOUS

Normal examples:

TIME_SYNC_STARTED
TIME_SYNC_SAMPLE
LATENCY_ESTIMATE_UPDATED

Critical events should have stronger retention handling.

However:

never block the exact precision fire moment waiting for physical disk
flush.

==================================================
41. LOG ROTATION AND RETENTION
==================================================

Configuration:

logging:
  directory: "./logs"
  rotation: "DAILY_UTC"
  retention-days: 90
  async-queue-capacity: 10000
  index-on-startup: true

Retention cleanup:

- must not run on precision thread
- must not delete active file
- must be observable
- must handle permission failures safely

==================================================
42. APPLICATION-LEVEL LOG SEARCH
==================================================

The user must be able to search historical logs from inside the JavaFX
application.

Create:

SearchableLogRepository

FileBackedLogRepository

LogIndex

LogIndexBuilder

LogIndexEntry

LogSearchService

LogSearchQuery

LogSearchResult

Support:

- from timestamp
- to timestamp
- event type
- sequence
- minimum S1
- maximum S1
- minimum S2
- maximum S2
- minimum RTT
- maximum RTT
- minimum confidence
- accepted/rejected
- estimation strategy
- free text where justified

Support:

- pagination
- stable ordering
- newest first
- oldest first
- configurable maximum page size

==================================================
43. FILE INDEX
==================================================

JSONL files remain the durable source of truth.

Create an in-memory metadata index.

LogIndexEntry should contain fields similar to:

- Path file
- long byteOffset
- int byteLength
- Instant timestamp
- LogEventType eventType
- Long sequence
- Long s1Nanos
- Long s2Nanos
- Long rttNanos
- Double confidence
- Boolean accepted
- String estimationStrategy

Do not keep every full historical event forever in memory.

On application startup:

1. scan log directory
2. discover JSONL files
3. parse valid lines
4. build index
5. tolerate malformed lines
6. tolerate partially written final line
7. expose search

Search must work after application restart.

==================================================
44. RUNTIME SETTINGS PERSISTENCE
==================================================

Create:

RuntimeSettingsRepository

Implementation:

FileBackedRuntimeSettingsRepository

File:

config/runtime-settings.json

Persist user-editable runtime settings such as:

- sync interval
- selected display zone
- selected timing options where appropriate

Example:

{
  "syncInterval": "PT30S",
  "displayZoneId": "Asia/Tashkent",
  "configurationVersion": 12
}

Do not store bearer tokens in this file.

Configuration precedence must be explicit.

Recommended:

1. secure runtime/environment secrets
2. runtime-settings.json for user runtime settings
3. application.yaml defaults

Document this.
==================================================
45. NETWORK / VPN CHANGE HANDLING
==================================================

Create:

NetworkEnvironmentMonitor

NetworkEnvironmentSnapshot

NetworkChangeDetector

The application may run through VPN.

Detect meaningful environment changes where practical:

- active network interface change
- VPN reconnect signal where observable
- local address change
- sustained RTT regime shift

When network regime changes:

- reduce confidence of previous latency model
- avoid blindly mixing old and new sample regimes
- request fresh measurements
- recalculate plan
- log NETWORK_ENVIRONMENT_CHANGED

Do not claim perfect VPN detection across all systems.

==================================================
46. SUSPEND / RESUME DETECTION
==================================================

Handle system suspend/resume risk.

A long unexpected scheduling gap may invalidate:

- clock freshness
- execution plan assumptions
- latency history confidence

Create explicit detection policy.

After suspected resume:

- mark clock potentially stale
- request fresh sync
- recalculate plan
- avoid using frozen ancient timing assumptions

==================================================
47. CONCURRENCY MODEL
==================================================

Use separate responsibilities:

1. JavaFX application thread
2. Time synchronization executor
3. Planning/update executor
4. Dedicated precision platform thread
5. Action execution path
6. Async JSONL writer thread
7. Optional log search/index executor
8. Network monitoring executor

Do not let:

- JavaFX rendering
- log search
- JSONL disk writes
- periodic sync
- JSON parsing
- retention cleanup

block the final precision scheduler.

Use immutable snapshots.

Use atomic publication where appropriate:

AtomicReference<ServerClockSnapshot>

AtomicReference<ExecutionPlan>

Document happens-before guarantees.

==================================================
48. UI - MAIN WINDOW
==================================================

Create a JavaFX main window with navigation.

Views:

1. Dashboard
2. Time Sync Settings
3. Timing History
4. Execution Audit
5. Settings

Use FXML.

Keep controllers thin.

Controllers delegate to application services.

==================================================
49. UI - DASHBOARD
==================================================

Dashboard must show:

- application state
- estimated server time
- target PLAY_TIME
- display time zone
- current trusted S1
- current trusted S2
- current RTT
- current jitter
- confidence
- active sync interval
- last sync
- next sync
- clock freshness
- plan version
- current execution status

Controls:

- set target date
- set target time with milliseconds
- ARM TARGET
- CANCEL
- SYNC NOW

The estimated server time display may refresh frequently in UI, but the
UI refresh must not become the timing source of truth.

==================================================
50. UI - TIME SYNC SETTINGS
==================================================

Show:

- current Time API
- active interval
- editable interval
- time unit selector
- Apply Interval
- Sync Now
- current sync status
- last started
- last completed
- last successful
- next planned
- last failure

Status examples:

IDLE
SYNCING
SUCCESS
FAILED
SKIPPED_ALREADY_RUNNING

==================================================
51. UI - TIMING HISTORY
==================================================

Create a searchable Timing History view.

Filters:

- from
- to
- event type
- sequence
- S1 min/max
- S2 min/max
- RTT min/max
- minimum confidence
- accepted/rejected
- estimation strategy

Table columns:

- timestamp
- event type
- sequence
- S1 ms
- S2 ms
- RTT ms
- jitter ms
- confidence
- accepted

Support pagination.

Run expensive searching outside JavaFX UI thread.

==================================================
52. UI - EXECUTION AUDIT
==================================================

Show final execution records.

Columns/details:

- target server time
- selected S1
- selected S2
- sample sequence
- plan version
- clock version
- desired fire time
- actual fire
- scheduler error
- outcome
- acknowledgment

Allow opening full audit details.

==================================================
53. UI THREADING
==================================================

Never block JavaFX thread with:

- HTTP requests
- file scanning
- index rebuilding
- JSON parsing of large history
- sleeps
- precision waiting
- log search

Use background services and publish immutable UI snapshots.

==================================================
54. CONFIGURATION
==================================================

Create:

config/application.yaml

Example defaults:

server:
  time-url: "http://172.16.14.19:5001/api/Cabinet/CheckTimeV2"
  timestamp:
    json-pointer: "/data"
    format: "EPOCH_MILLIS_DECIMAL"
    semantics: "UNKNOWN"
  response:
    success-json-pointer: "/success"
    error-json-pointer: "/error"
    expected-success-value: "true"

http:
  connect-timeout: "PT3S"
  request-timeout: "PT2S"

timing:
  sync-interval: "PT60S"
  min-sync-interval: "PT5S"
  max-sync-interval: "PT1H"
  sample-window-size: 30
  max-clock-age: "PT3M"
  minimum-confidence: 0.70
  final-freeze-window: "PT2S"
  coarse-threshold: "PT0.05S"
  spin-threshold: "PT0.002S"
  execution-overhead: "PT0S"

logging:
  directory: "./logs"
  retention-days: 90
  async-queue-capacity: 10000
  index-on-startup: true

Do not put secrets here.

==================================================
55. ERROR MODEL
==================================================

Create explicit typed errors where useful.

Handle:

- time API unavailable
- connect timeout
- request timeout
- non-2xx response
- malformed response
- malformed timestamp
- unsupported timestamp format
- S1/S2 strategy failure
- low confidence
- stale clock
- target already passed
- target invalid
- action failure
- ambiguous timeout
- disk full
- permission denied
- malformed JSONL
- partially written JSONL line
- queue overflow
- invalid runtime interval
- scheduler cancellation
- system resume
- network regime change

Do not silently swallow failures.

==================================================
56. TESTING STRATEGY
==================================================

Create serious tests.

Unit tests:

- Duration conversions
- timestamp parsing
- timestamp semantics
- RTT calculation
- symmetric S1/S2 fallback
- historical estimator
- outlier filter
- zero MAD behavior
- jitter
- quality score
- sample window bound
- server clock progression
- stale clock
- execution plan formula
- S1 compensation
- execution overhead compensation
- server time to monotonic deadline conversion
- state transitions
- one-shot guard
- runtime interval validation
- configuration version increment

==================================================
57. DETERMINISTIC CLOCK TESTS
==================================================

Use:

FakeNanoClock

Fixed/fake WallClock

Do not wait real 60 seconds.

Tests must advance fake time programmatically.

==================================================
58. DYNAMIC INTERVAL TESTS
==================================================

Test:

- default 60 sec
- 60 -> 30
- 30 -> 10
- invalid zero
- invalid negative
- below minimum
- above maximum
- old schedule replaced
- no duplicate scheduler
- runtime setting persisted
- restart restores setting
- configuration version increment
- next planned time updated

==================================================
59. SYNC CONCURRENCY TESTS
==================================================

Test:

- periodic sync running + periodic tick
- periodic sync running + Sync Now
- Sync Now running + periodic tick
- no accidental overlap
- skip policy
- delayed policy if implemented
- skipped event logging

==================================================
60. MOCK TIME SERVER
==================================================

Create integration-test mock server.

Support scenarios:

- normal timestamp
- fixed delay
- jitter
- latency spike
- timeout
- malformed JSON
- missing field
- non-2xx
- epoch millis
- epoch seconds
- ISO-8601

Where possible, simulate asymmetric conceptual timing conditions while
making clear that ordinary client RTT cannot directly observe true
one-way latency.

==================================================
61. PRECISION SCHEDULER TESTS
==================================================

Test:

- absolute deadline
- exactly one fire
- cancellation
- plan replacement before freeze
- plan freeze
- no plan churn after freeze
- actual fire marker
- scheduler error calculation
- no duplicate action
- simultaneous race

Use fake clocks where practical.

Do not require tests to rely on real millisecond accuracy of CI machines.

==================================================
62. JSONL TESTS
==================================================

Test:

- one complete JSON object per line
- append
- restart
- index rebuild
- time range search
- event search
- sequence search
- S1 range
- S2 range
- RTT range
- confidence
- accepted/rejected
- pagination
- sorting
- malformed line
- partial final line
- concurrent append and search
- rotation
- retention
- queue overflow
- critical event handling
- disk failure simulation where practical

==================================================
63. APPLICATION STARTUP
==================================================

Startup sequence:

1. load application defaults
2. load runtime settings
3. initialize secure token provider
4. create clocks
5. create reusable HttpClient
6. create parser
7. create time client
8. create latency engine
9. create JSONL writer
10. rebuild log index
11. create server clock
12. create sync scheduler
13. create planning engine
14. create precision scheduler
15. create action executor
16. create state machine
17. create JavaFX views
18. perform optional initial sync
19. show readiness state

Do not block JavaFX thread on long startup work.

Show startup status where appropriate.

==================================================
64. APPLICATION SHUTDOWN
==================================================

Shutdown sequence must:

- stop accepting new target execution
- cancel periodic sync
- cancel precision scheduler safely
- stop network monitor
- flush queued logs with bounded timeout
- close executors
- persist runtime settings
- avoid hanging indefinitely

Test important shutdown components.

==================================================
65. README
==================================================

Create a professional README explaining:

- application purpose
- important timing limitations
- best-effort precision disclaimer
- Java 21 requirement
- how to build
- how to run
- how to configure Time API
- timestamp response parser config
- how to set auth token securely
- runtime sync interval
- Sync Now
- PLAY_TIME
- JSONL logs
- log search
- dry-run mode
- testing
- package architecture

Document explicitly:

The application cannot guarantee hard real-time execution on a normal
JVM and general-purpose OS.

Document explicitly:

Exact independent S1/S2 values require additional assumptions or
measurement data beyond ordinary HTTP RTT.

==================================================
66. IMPLEMENTATION PHASES
==================================================

Work autonomously through these phases.

Do not stop after merely describing architecture.

PHASE 1

Create Maven project.

Create complete package structure.

Create configuration models.

Create clock abstractions.

Run compile.

PHASE 2

Implement:

ServerTimeClient
HttpServerTimeClient
response parser
timestamp format
timestamp semantics
TimeMeasurement

Run tests.

PHASE 3

Implement:

S1S2EstimationStrategy
ProvidedS1S2Strategy
SymmetricRttFallbackStrategy
HistoricalCalibratedStrategy
LatencySample
SampleWindow
OutlierFilter
JitterEstimator
SampleQualityScorer
RobustLatencyEstimator

Run tests.

PHASE 4

Implement:

ServerClock
ServerClockModel
ServerClockSnapshot
stale clock policy

Run tests.

PHASE 5

Implement:

RuntimeSettingsRepository
SyncIntervalService
DynamicSyncScheduler
Sync Now
overlap policy
TimeSyncCoordinator
status snapshot

Run tests.

PHASE 6

Implement:

TargetTimeService
ExecutionPlan
ExecutionPlanCalculator
ActionOverheadEstimator

Run tests.

PHASE 7

Implement:

PrecisionScheduler
HybridPrecisionScheduler
CancellationToken
freeze window
state machine

Run tests.

PHASE 8

Implement:

ActionExecutor
DryRunActionExecutor
UiButtonActionExecutor
HttpActionExecutor adapter
OneShotGuard
outcome model
retry policy

Run tests.

PHASE 9

Implement:

JSONL event model
AsyncJsonlLogWriter
rotation
retention
priority
failure handling

Run tests.

PHASE 10

Implement:

FileBackedLogRepository
LogIndex
LogIndexBuilder
LogSearchService
search filters
pagination

Run tests.

PHASE 11

Implement JavaFX UI:

Main Window
Dashboard
Time Sync Settings
Timing History
Execution Audit
Settings

Keep controllers thin.

Run application.

PHASE 12

Implement:

NetworkEnvironmentMonitor
resume detection
confidence invalidation policies

Run tests.

PHASE 13

Create integration mock time server.

Run all tests.

PHASE 14

Perform principal-level self-review.

==================================================
67. PRINCIPAL-LEVEL SELF REVIEW
==================================================

Review specifically for:

- race conditions
- stale plan race
- freeze window race
- cancellation race
- duplicate action race
- overlap race
- interval reschedule race
- manual/periodic sync race
- atomic publication correctness
- happens-before correctness
- unit mismatch
- epoch/nano confusion
- overflow risks
- negative durations
- stale clock
- low confidence
- incorrect S1/S2 assumptions
- inaccurate timestamp semantics
- disk I/O on timing thread
- UI blocking
- unbounded queue
- unbounded memory
- corrupted JSONL
- partial line
- index inconsistency
- shutdown deadlock
- executor leak
- HttpClient misuse
- secret logging
- ambiguous timeout handling

Refactor weak areas.

==================================================
68. FINAL ACCEPTANCE CRITERIA
==================================================

The project is complete only when:

1. mvn test passes

2. mvn package passes

3. JavaFX app starts

4. Time API URL is configurable

5. Default sync interval is 60 seconds

6. Sync interval can be changed from UI without restart

7. Sync Now works

8. Old periodic schedule does not continue after interval change

9. Overlapping sync is prevented by explicit policy

10. S1/S2 strategy architecture exists

11. Symmetric fallback is explicitly labeled as an assumption

12. Historical samples are logged

13. JSONL files are human readable

14. Logs survive restart

15. Logs are searchable from inside the application

16. ServerClockModel uses monotonic progression

17. ExecutionPlan uses explicit compensation

18. Precision scheduler uses absolute monotonic deadline

19. Freeze window exists

20. OneShotGuard prevents duplicate primary action

21. Final execution is auditable back to:
    - S1
    - S2
    - sample sequence
    - clock version
    - plan version

22. No blocking disk I/O occurs on precision thread

23. No real bearer token exists in source

24. Final action endpoint is not fabricated

25. README documents timing limitations

26. All tests pass

==================================================
69. IMPORTANT OUTPUT BEHAVIOR
==================================================

Do not only give me a plan.

Actually create the complete repository.

Create files.

Write production code.

Write tests.

Compile repeatedly.

Fix compile errors.

Run tests.

Fix failing tests.

Continue until the project is in a coherent working state.

When an exact external integration detail is unknown, such as:

- real CheckTimeV2 response JSON field
- real final action endpoint
- real ProvidedS1S2 algorithm

do NOT invent production behavior.

Instead:

- create a clean interface
- create configurable adapters
- provide safe defaults
- provide dry-run mode
- document the integration point clearly

The final result must be a professional foundation that can accept the
real external details without rewriting the core architecture.

Start now by creating the Maven project and package structure, then
continue through all implementation phases.

==================================================
70. RUNTIME BEARER TOKEN AUTHENTICATION
    ==================================================

The real server time endpoint requires Bearer authentication.

The currently known request shape is conceptually equivalent to:

GET
http://172.16.14.19:5001/api/Cabinet/CheckTimeV2

Headers:

Accept: application/json

User-Agent:
SpotAppV2-04092025-Microsoft Windows NT 6.2.9200.0

Content-Type:
application/json

Authorization:
Bearer <RUNTIME_TOKEN>

IMPORTANT:

Do not hardcode any real Bearer token.

Do not copy a token from examples, prompts, documentation, tests, logs,
or source comments into production configuration.

The Bearer token is expected to expire and must be replaceable at runtime
from the JavaFX application UI without restarting the application.

==================================================
71. RUNTIME AUTH TOKEN UI
    ==================================================

Add a dedicated authentication section to the JavaFX application.

The user must be able to enter or replace the Bearer token from the
application window.

Required controls:

- masked Bearer Token input
- Apply Token button
- Clear Token button
- optional Show/Hide token control
- Test Authentication button
- authentication status display
- token expiration display when safely derivable from JWT metadata
- remaining lifetime display when safely derivable

Use JavaFX PasswordField by default.

Conceptual UI:

--------------------------------------------------
API Authentication
--------------------------------------------------

Bearer Token:
[ ********************************************** ]

[ Show ]
[ Apply Token ]
[ Clear Token ]
[ Test Authentication ]

Status:
AUTHENTICATED

Token Expires:
2026-07-05T15:30:00Z

Remaining:
2h 14m

--------------------------------------------------

The UI controller must not perform HTTP logic directly.

The UI delegates to:

RuntimeAuthTokenService

and:

AuthTokenProvider

==================================================
72. AUTH TOKEN DOMAIN MODEL
    ==================================================

Create:

public interface AuthTokenProvider

Conceptual methods:

Optional<String> currentBearerToken();

TokenStatusSnapshot status();

Create:

RuntimeAuthTokenService

Responsibilities:

- accept token from UI
- normalize token input
- reject blank token
- safely replace active token
- clear token
- publish authentication state
- expose token metadata without exposing token value
- notify HTTP clients that future requests use the new token

The user may paste either:

Bearer eyJ...

or:

eyJ...

Normalize input so the internal provider stores the token value without
duplicating the "Bearer " prefix.

When creating the HTTP Authorization header:

Authorization: Bearer <token>

Add the prefix exactly once.

==================================================
73. THREAD-SAFE TOKEN REPLACEMENT
    ==================================================

Runtime token replacement must be thread-safe.

A token may be changed while periodic synchronization is active.

Use immutable token snapshots and atomic publication.

Create:

AuthTokenSnapshot

Possible metadata fields:

- boolean present
- Instant updatedAt
- long version
- TokenSource source
- Instant jwtExpiresAt when available
- Instant jwtNotBefore when available
- String jwtSubject when available and safe
- AuthTokenState state

Do NOT expose the raw token through status snapshots.

Publish active token state atomically.

For example:

AtomicReference<InternalTokenHolder>

or another justified thread-safe design.

A new HTTP request must obtain the latest active token at request creation
time.

Do not permanently capture an old token inside HttpServerTimeClient
construction.

Bad:

new HttpServerTimeClient(tokenAtStartup)

Better:

HttpServerTimeClient(AuthTokenProvider provider)

Then for each request:

provider.currentBearerToken()

==================================================
74. AUTH TOKEN STATES
    ==================================================

Create explicit states:

NO_TOKEN
TOKEN_PRESENT
TOKEN_EXPIRING_SOON
TOKEN_EXPIRED
AUTHENTICATED
AUTH_REJECTED
UNKNOWN

Do not use unrelated booleans.

Authentication state is separate from the main precision execution state.

The UI should clearly show when server synchronization cannot continue
because authentication is unavailable or rejected.

==================================================
75. JWT EXPIRATION AWARENESS
    ==================================================

The runtime token may be a JWT.

If the token structurally looks like a JWT:

header.payload.signature

the application may decode JWT payload metadata locally for UX purposes.

Support reading standard claims where present:

exp
nbf
iat
sub

IMPORTANT SECURITY RULE:

Local JWT payload decoding is NOT cryptographic verification.

Do not treat locally decoded claims as proof that the token is authentic.

Use decoded exp/nbf only for:

- UI expiration visibility
- proactive warning
- avoiding obviously expired token usage where policy allows

The actual server remains authoritative for authentication.

Create:

JwtMetadataInspector

Do not require JWT decoding for opaque Bearer tokens.

Opaque tokens must remain supported.

==================================================
76. TOKEN EXPIRATION BEHAVIOR
    ==================================================

If JWT exp indicates the token is expired:

- mark state TOKEN_EXPIRED
- show clear UI warning
- do not silently continue repeated authenticated requests forever
- allow user to paste a replacement token
- do not automatically invent a refresh flow

If token expiration is approaching:

Default warning threshold:
5 minutes

Configurable.

Example state:

TOKEN_EXPIRING_SOON

The application may display:

"Bearer token expires in 4 minutes. Replace it to avoid losing time
synchronization."

Do not block an otherwise valid token solely because local unverified
metadata says it is near expiry.

==================================================
77. HTTP AUTH HEADER INJECTION
    ==================================================

Create:

AuthorizationHeaderProvider

or equivalent adapter.

For every CheckTimeV2 request:

1. query AuthTokenProvider for the current token
2. if absent:
   return explicit AUTH_TOKEN_MISSING failure
3. build:

Authorization: Bearer <current-token>

4. send request

Never log the Authorization header.

Never include raw token values in:

- SLF4J logs
- JSONL event logs
- exceptions
- UI status snapshots
- execution audit
- debug output
- README
- test snapshots

==================================================
78. REAL CHECKTIMEV2 REQUEST PROFILE
    ==================================================

Model the server time request using the known request profile.

Default endpoint:

http://172.16.14.19:5001/api/Cabinet/CheckTimeV2

Default method:

GET

Default headers:

Accept:
application/json

User-Agent:
SpotAppV2-04092025-Microsoft Windows NT 6.2.9200.0

Content-Type:
application/json

Authorization:
Bearer <runtime token>

Make the User-Agent externally configurable.

Example:

server:
time-url: "http://172.16.14.19:5001/api/Cabinet/CheckTimeV2"

request:
accept: "application/json"
content-type: "application/json"
user-agent: "SpotAppV2-04092025-Microsoft Windows NT 6.2.9200.0"

Do not hardcode the real token.

Do not manually force the Host header unless technically required and
explicitly justified.

For a normal Java HttpClient request, the host is derived from the request
URI:

172.16.14.19:5001

Do not use unsafe restricted-header workarounds merely to duplicate curl's
explicit Host header.

==================================================
79. AUTHENTICATION TEST ACTION
    ==================================================

The UI must provide:

Test Authentication

This action should perform a real CheckTimeV2 request using the currently
entered runtime token.

Use the normal production ServerTimeClient path.

Do not create a fake authentication check.

Possible results:

SUCCESS
TOKEN_MISSING
UNAUTHORIZED
FORBIDDEN
TIMEOUT
NETWORK_ERROR
INVALID_RESPONSE

If successful:

- update auth UI state
- optionally process the measurement through the normal sync pipeline if
  explicitly configured

Default recommended behavior:

Test Authentication should use the same endpoint but should clearly
define whether the result becomes a latency sample.

Avoid accidentally polluting latency history with diagnostic requests
unless policy explicitly allows it.

==================================================
80. HTTP 401 / 403 HANDLING
    ==================================================

Handle authentication failures explicitly.

HTTP 401:

- mark authentication state AUTH_REJECTED
- emit authentication failure event
- show UI warning
- request user to provide a new token
- do not blindly retry in a tight loop

HTTP 403:

- distinguish from 401
- report FORBIDDEN
- do not automatically assume token expiration
- preserve diagnostic status without leaking response secrets

Do not automatically fabricate token refresh behavior.

==================================================
81. AUTH FAILURE AND PERIODIC SYNC
    ==================================================

If periodic synchronization receives 401 Unauthorized:

Default behavior:

1. mark current auth state AUTH_REJECTED
2. record failure
3. prevent aggressive repeated unauthorized calls
4. show user-visible warning
5. allow immediate token replacement from UI
6. after successful token replacement, allow synchronization to resume

Define a configurable backoff policy for repeated auth failures.

Do not generate a request storm with an expired token.

Example:

auth:
unauthorized-backoff: "PT30S"

A user pressing Apply Token with a new token may trigger an immediate
authentication test or immediate Sync Now according to explicit policy.

==================================================
82. APPLY TOKEN BEHAVIOR
    ==================================================

When the user enters a new token and presses:

Apply Token

perform:

1. validate non-empty input
2. normalize optional "Bearer " prefix
3. create new immutable internal token snapshot
4. increment token version
5. atomically publish new token
6. inspect JWT metadata when structurally possible
7. update UI state
8. emit safe audit event without token value
9. optionally trigger authentication validation according to configuration

The application must not restart.

The periodic scheduler must use the new token on future requests.

An already-running HTTP request may finish using the previous token.
Document this behavior.

Do not attempt unsafe mid-flight mutation of an already-dispatched HTTP
request.

==================================================
83. CLEAR TOKEN BEHAVIOR
    ==================================================

When user presses:

Clear Token

perform:

- remove active runtime token
- increment token version
- mark state NO_TOKEN
- prevent future authenticated time-sync requests
- emit safe audit event
- update UI

Never log the previous token.

==================================================
84. TOKEN PERSISTENCE POLICY
    ==================================================

Default token persistence policy:

MEMORY_ONLY

The raw Bearer token must NOT be stored in:

config/runtime-settings.json

application.yaml

JSONL logs

Logback logs

README

source code

Default lifecycle:

User enters token
->
token held in memory
->
requests use token
->
application exits
->
token is not restored automatically

Create:

enum TokenPersistencePolicy {
MEMORY_ONLY,
SECURE_OS_STORAGE
}

Implement MEMORY_ONLY completely.

SECURE_OS_STORAGE may remain an explicit future adapter unless a proper
platform-specific secure implementation is intentionally provided.

Do not implement insecure plain-text persistence as a convenience.

==================================================
85. TOKEN MEMORY HANDLING
    ==================================================

Minimize unnecessary copies of sensitive token values.

Be realistic:

Java String immutability means perfect zeroization cannot be guaranteed.

Do not make false security claims.

Where practical:

- avoid storing token in multiple objects
- avoid concatenating token into logs
- avoid exception messages containing token
- avoid retaining UI field text after successful Apply
- clear the input control after token acceptance if UX policy allows

The actual Authorization header may require a String representation.

Document limitations honestly.

==================================================
86. AUTH AUDIT EVENTS
    ==================================================

Add safe JSONL event types:

AUTH_TOKEN_APPLY_REQUESTED
AUTH_TOKEN_APPLIED
AUTH_TOKEN_CLEARED
AUTH_TOKEN_EXPIRED
AUTH_TOKEN_EXPIRING_SOON
AUTH_TEST_STARTED
AUTH_TEST_SUCCEEDED
AUTH_TEST_FAILED
AUTH_UNAUTHORIZED
AUTH_FORBIDDEN

Persist metadata only.

Example:

{
"eventType": "AUTH_TOKEN_APPLIED",
"timestamp": "2026-07-05T12:30:00Z",
"tokenVersion": 7,
"source": "APPLICATION_UI",
"jwtExpiresAt": "2026-07-05T15:30:00Z",
"tokenValueLogged": false
}

Never persist:

rawToken
authorizationHeader
jwtSignature
full JWT string

==================================================
87. AUTH STATUS UI SNAPSHOT
    ==================================================

Create immutable:

AuthStatusSnapshot

Fields may include:

- AuthTokenState state
- boolean tokenPresent
- long tokenVersion
- Instant updatedAt
- Instant jwtExpiresAt
- Duration remainingLifetime
- Instant lastAuthTestAt
- String lastFailureCode
- String safeLastFailureMessage

Never include raw token value.

Publish status safely to JavaFX.

==================================================
88. TOKEN REPLACEMENT DURING FINAL WINDOW
    ==================================================

Handle token replacement during:

ARMED
FINALIZING
FIRING

explicitly.

If token changes during ARMED:

- future requests use new token
- execution plan timing must not be recalculated merely because token text
  changed unless network/timing measurements also change

If token changes during FINALIZING:

- atomically publish new token for future request construction
- do not destabilize frozen timing plan
- do not trigger unnecessary time synchronization inside the protected
  final window

If the final action request has already been constructed/dispatched:

- do not mutate it in flight
- record which token version was used
- NEVER record the token value

ExecutionAudit should include:

authTokenVersion

not:

authToken

==================================================
89. TOKEN VERSION IN EXECUTION AUDIT
    ==================================================

Add:

authTokenVersion

to relevant execution audit metadata.

This allows answering:

"Which runtime credential version was active when the action was sent?"

without exposing the credential.

Example:

{
"eventType": "ACTION_FIRED",
"planVersion": 18,
"clockVersion": 22,
"authTokenVersion": 7
}

==================================================
90. AUTHENTICATION TESTS
    ==================================================

Add tests for:

- no token
- blank token rejected
- raw JWT token accepted
- "Bearer <token>" input normalized
- Bearer prefix not duplicated
- runtime replacement without restart
- future request uses new token
- already-running request behavior documented/tested where practical
- token clear
- 401 handling
- 403 handling
- unauthorized backoff
- token version increment
- raw token absent from logs
- raw token absent from JSONL
- raw token absent from exceptions
- JWT exp decoding
- malformed JWT
- opaque token support
- expired JWT metadata
- expiring-soon warning
- auth test success
- auth test failure
- concurrent token replacement and sync request
- token replacement during ARMED
- token replacement during FINALIZING
- execution audit contains token version but not token
- runtime-settings.json never contains raw token

Use synthetic test tokens only.

Never use a real production token in tests.





## Current explicit resolution policy

The following production contracts remain unresolved and must not be fabricated in source code:

- CheckTimeV2 timestamp semantics
- Final action API contract
- Real Provided S1/S2 business algorithm
- Final action acknowledgment payload contract

Use explicit interfaces, configurable adapters, and safe defaults only.

Default auth policy is MEMORY_ONLY: the raw bearer token is entered in the JavaFX UI, held only in memory, is not persisted across restarts, and startup begins in NO_TOKEN / AUTH REQUIRED state.

