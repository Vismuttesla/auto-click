# Unresolved Integrations Register

This document records external contracts that are intentionally unresolved. The application must not fabricate these contracts in source code.

## 1. CheckTimeV2 timestamp semantics

- Status: UNRESOLVED EXTERNAL SEMANTIC CONTRACT
- Known now: endpoint, response shape, `/success`, `/error`, `/data`, decimal numeric time format, and observed success value `"true"`.
- Still unknown: the exact server-side instant represented by `/data` and whether it reflects request receipt, response creation, response send time, or another internal moment.
- Current safe adapter/fallback: `TimestampSemantics.UNKNOWN` with explicit conversion from decimal epoch milliseconds.
- Exact information required to complete integration: authoritative meaning of the sampled server timestamp and any rules for deriving clock offset from it.

## 2. Final action API contract

- Status: UNRESOLVED EXTERNAL CONTRACT
- Why unresolved: The real final action endpoint, method, headers, and body are not known.
- Current safe adapter/fallback: `DryRunActionExecutor` as the default working executor; `HttpActionExecutor` may exist only as a configurable adapter foundation.
- Exact information required to complete integration: Endpoint URL, HTTP method, request headers, request body schema, success response contract, failure response contract, and idempotency rules.

## 3. Real Provided S1/S2 business algorithm

- Status: UNRESOLVED EXTERNAL BUSINESS ALGORITHM
- Why unresolved: The real provider algorithm has not been supplied.
- Current safe adapter/fallback: `ExternalS1S2Provider` SPI, `UnconfiguredExternalS1S2Provider`, and explicit `SYMMETRIC_RTT_FALLBACK` assumption-based strategy.
- Exact information required to complete integration: The real provider algorithm, required inputs, output semantics, and confidence/validation rules.

## 4. Final action acknowledgment payload contract

- Status: UNRESOLVED EXTERNAL CONTRACT
- Why unresolved: The real acknowledgment payload semantics are not known.
- Current safe adapter/fallback: `ActionAcknowledgementParser` operating on `RawActionHttpResponse`, with sealed results such as `ActionConfirmed`, `ActionRejected`, `ActionAmbiguous`, and `ActionUnrecognized`.
- Exact information required to complete integration: Definitive acknowledgment payload fields, business success criteria, and any response codes or body markers that determine confirmation or rejection.
