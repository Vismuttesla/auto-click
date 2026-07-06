# Repository Guidelines

## Project Structure & Module Organization
This repository contains a Maven-based Java 21 application. Core sources live under `src/main/java/com/abbos/precisiontrigger/`, tests under `src/test/java/`, and runtime defaults under `src/main/resources/config/`.

Keep timing, HTTP, parsing, and UI code in separate packages so the precision engine stays independent from adapters.

## Build, Test, and Development Commands
Use Maven for local development:
- `mvn test` runs the JUnit 5 suite
- `mvn clean test` removes prior build output and reruns tests
- `mvn package` builds the application artifact

Use deterministic tests for clock-sensitive code; avoid relying on wall-clock timing.

## Coding Style & Naming Conventions
Target Java 21. Use 4-space indentation and standard Java naming.
- Packages: all lowercase, for example `com.abbos.precisiontrigger.checktime`
- Types: `PascalCase`
- Methods and fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`

Prefer immutable records for value objects and explicit abstractions for time, network, and parsing boundaries. Do not hardcode bearer tokens or production endpoints.

## Testing Guidelines
Use JUnit 5 for tests, with AssertJ where readable assertions help. Name tests `*Test` for unit tests and `*IT` for integration tests.

Favor deterministic tests by injecting clocks and fakes instead of sleeping in test code. Add coverage around time calculations, parsing, and duplicate-action prevention.

## Commit & Pull Request Guidelines
This repository has no commit history yet, so no local convention is established. Use short, imperative commit subjects such as `Add time parser tests`.

Pull requests should include:
- A concise summary of the change
- The commands used to verify it
- Screenshots or screen recordings for UI changes
- Notes for any unresolved external contract or fallback behavior

## Security & Configuration Tips
Treat unresolved integrations as external dependencies, not implementation details. Keep any future secrets, tokens, and live endpoints out of source control and document them only in configuration notes or environment-specific setup files.
