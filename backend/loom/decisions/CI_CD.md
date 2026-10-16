# ADR: GitHub Actions CI/CD

**Status:** Accepted  
**Date:** 2026-07-26 (updated 2026-10-16)  
**Scope:** `.github/workflows/ci.yml`

---

## Context

The backend module is a Gradle project under `backend/loom/`. It needs automated verification on every push and pull request so that broken builds and failing tests are caught before merging. The artifact produced by the build — `loom-engine.jar` — must be verified end-to-end: (1) the SLM integration must pass against a live Ollama model, and (2) the Flutter application must successfully boot against the same JAR and pass its integration checks.

---

## Decision

Use a GitHub Actions workflow with five sequenced jobs:

```
build-and-test ──→ integration  ──→ flutter-boot ──→ package
               ──→ flutter-unit ──────────────────────────↗
```

Every downstream job runs only when `build-and-test` passes. `flutter-boot` additionally requires `integration` to pass — Flutter never boots the JAR against an SLM that has not been validated. `package` requires both `flutter-boot` and `flutter-unit` to pass — the artifact is promoted only after the full verification chain succeeds.

---

## Workflow

**File:** [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)

### Triggers

```yaml
on:
  push:
    branches: ["main"]
    paths:
      - "backend/loom/**"
      - "frontend/**"
      - ".github/workflows/ci.yml"
  pull_request:
    branches: ["main"]
    paths:
      - "backend/loom/**"
      - "frontend/**"
      - ".github/workflows/ci.yml"
```

The `paths` filter restricts runs to changes inside `backend/loom/`, `frontend/`, or the workflow file itself.

---

### Job 1: `build-and-test`

| Step | Command | Purpose |
|---|---|---|
| Checkout | `actions/checkout@v4` | Full source tree |
| Set up Java | `actions/setup-java@v4` (Temurin 21) | Matches local dev JVM; cache keyed to wrapper properties |
| Compile | `./gradlew compileJava compileTestJava` | Fail fast on compilation errors before running tests |
| Test | `./gradlew test` | JUnit 5 suite — excludes `@Tag("integration")` |
| Upload test report | `actions/upload-artifact@v4` | HTML test report, 7-day retention, uploaded even on failure |
| Build shadow JAR | `./gradlew shadowJar` | Fat JAR built after tests reusing compiled classes |
| Upload JAR (staging) | `actions/upload-artifact@v4` | 1-day staging artifact for downstream jobs |

---

### Job 2: `integration`

Runs the SLM integration BVT (`SLMIntegrationBVT`) against a live `loom-engine.jar` and a local Ollama instance. Requires `build-and-test`.

| Step | Purpose |
|---|---|
| Download shadow JAR | Fetches `loom-engine-staging` from job 1 |
| Install Ollama | `curl -fsSL https://ollama.com/install.sh \| sh` |
| Cache Ollama model | `actions/cache@v4` keyed to `ollama-qwen2.5-coder-1.5b` |
| Start Ollama server | Background process; poll until port 11434 responds |
| Pull model | Skipped if cache hit; pulls `qwen2.5-coder:1.5b` otherwise |
| Boot `loom-engine.jar` | `java -jar dist/loom-engine.jar` in background |
| Wait for port + token | Polls `/tmp/loom.log` for `LOOM_PORT=` and `LOOM_TOKEN=` lines; exports both to `$GITHUB_ENV` |
| `./gradlew integrationTest` | Runs `SLMIntegrationBVT` (selected by `includeTags("integration")` in the task definition); receives base URL + token via `LOOM_INTEGRATION_BASE_URL` / `LOOM_INTEGRATION_TOKEN` env vars |
| Upload integration test report | `actions/upload-artifact@v4`, 7-day retention |

#### Why `SLMIntegrationBVT` uses system properties, not env vars directly

`build.gradle.kts` reads `LOOM_INTEGRATION_BASE_URL` and `LOOM_INTEGRATION_TOKEN` from the environment and passes them to JUnit via `systemProperty(...)`. This keeps the test class free of env-var lookups and makes the values available locally via `-Dloom.integration.baseUrl=...` without changing the CI workflow.

---

### Job 3: `flutter-boot`

Runs the Flutter integration test against a freshly booted `loom-engine.jar`. Requires `integration` — Flutter never runs the boot test against an engine that has not already passed the SLM BVT.

| Step | Purpose |
|---|---|
| Set up Java 21 | Required to boot the JAR on the Flutter runner |
| Set up Flutter 3.47.5 | `subosito/flutter-action@v2` |
| Download shadow JAR | Same `loom-engine-staging` artifact |
| Install Ollama + pull model | Identical sequence to the `integration` job |
| Boot `loom-engine.jar` | Background process; stdout redirected to `/tmp/loom-flutter.log` |
| Wait for port + token | Same polling script; exports to `$GITHUB_ENV` |
| `flutter pub get` | Resolves dependencies |
| `xvfb-run flutter test integration_test/ --device-id linux` | Runs `app_test.dart` under a virtual display (required on `ubuntu-latest` for Flutter Linux desktop); `--dart-define=LOOM_ENGINE_URL` and `--dart-define=LOOM_TOKEN` injected from `$GITHUB_ENV` |
| Upload flutter-boot test report | `actions/upload-artifact@v4`, 7-day retention |

#### `integration_test/app_test.dart` design

The test file uses `flutter_test` + `integration_test` (no `patrol` / no device required — runs on `ubuntu-latest`):

1. **Reachability check** (`test`): `GET /health` using the `package:http` client with `LOOM_ENGINE_URL` and `LOOM_TOKEN` read from `--dart-define`. Skipped gracefully when the URL is not set (local runs without a running engine).
2. **Widget render check** (`testWidgets`): Pumps the `OnboardingWelcomeScreen` in a `ProviderScope` with mocked storage and a stub `EngineProcessService`. Asserts `'Loom'` and `'Your AI workforce, on your machine'` are present.

`IntegrationTestWidgetsFlutterBinding.ensureInitialized()` is called at the top of `main()` so the `integration_test` runner can collect results correctly.

---

### Job 4: `flutter-unit`

Runs Flutter static analysis and unit tests independently of the engine. Requires `build-and-test` (to gate on a green Java build) but runs in parallel with `integration`.

| Step | Purpose |
|---|---|
| `flutter pub get` | Resolves dependencies |
| `flutter analyze --no-fatal-infos` | Static analysis |
| `flutter test --reporter expanded` | Unit test suite in `test/` |

---

### Job 5: `package`

Pure artifact-promotion step. Requires both `flutter-boot` and `flutter-unit` — the artifact is promoted only after the full verification chain (Java unit tests → SLM BVT → Flutter boot test + Flutter unit tests) has passed.

| Step | Purpose |
|---|---|
| Download JAR | Fetches `loom-engine-staging` |
| Upload JAR | Publishes as `loom-engine` with 30-day retention |

No build tooling required — no Java install, no Gradle invocation.

---

## Key Decisions

### Sequential gate: `integration` before `flutter-boot`

Flutter must not boot the JAR until the SLM BVT has confirmed the engine behaves correctly against a live model. The `needs: integration` constraint enforces this. If Ollama is unavailable or the model call fails, the Flutter boot test never runs — no false positives.

### Ollama model cache shared across jobs

Both `integration` and `flutter-boot` use the same `actions/cache@v4` key (`ollama-qwen2.5-coder-1.5b`). On a cache hit, `ollama pull` is skipped entirely. On a miss (first run or after a key rotation), the model is pulled once per job. Because these jobs run on separate runners, each job may independently need to pull; the cache ensures the pull is skipped on subsequent runs.

### `--dart-define` for engine coordinates, not env vars

Flutter's `String.fromEnvironment` reads `--dart-define` values baked in at compile time. Passing the port and token this way avoids any platform-channel or `Platform.environment` lookup inside the test — the values are available as compile-time constants regardless of operating system.

### `patrol` removed from `integration_test/`

`patrol` requires a connected physical or virtual device, which is not available on `ubuntu-latest`. The integration test was rewritten to use `flutter_test` + `integration_test` (SDK package) so it runs headlessly. The widget render check preserved the same UI assertions as the previous patrol test.

### `flutter-unit` kept separate from `flutter-boot`

Static analysis and unit tests are fast and device-independent. Running them in a separate job means analysis failures appear quickly (in parallel with the slower `integration` job) rather than being delayed behind the Ollama model pull.

---

## Artifact Retention

| Artifact | Retention | Rationale |
|---|---|---|
| `test-report` | 7 days | Debugging recent unit test failures |
| `loom-engine-staging` | 1 day | Temporary hand-off: `build-and-test` → `integration`, `flutter-boot`, `package` |
| `loom-engine` | 30 days | Long enough for manual smoke tests and Flutter subprocess integration |
| `integration-test-report` | 7 days | Debugging SLM BVT failures |
| `flutter-boot-test-report` | 7 days | Debugging Flutter boot failures |

---

## What This Does Not Cover

- **Deployment** — the JAR is produced as an artifact but not pushed anywhere. A separate release workflow (triggered on tag) would handle publishing.
- **Version tagging** — `version = "1.0-SNAPSHOT"` in `build.gradle.kts`; the shadow JAR is always named `loom-engine.jar`.
- **macOS/Windows Flutter builds** — the `flutter-boot` job runs on `ubuntu-latest`. Platform-specific bundle testing (`.app`, `.exe`) is out of scope for this CI pipeline.
