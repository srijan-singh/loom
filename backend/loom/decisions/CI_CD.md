# ADR: GitHub Actions CI/CD

**Status:** Accepted  
**Date:** 2026-07-26  
**Scope:** `.github/workflows/ci.yml`

---

## Context

The backend module is a Gradle project under `backend/loom/`. It needs automated verification on every push and pull request so that broken builds and failing tests are caught before merging. The artifact produced by the build — `loom-engine.jar` — must be available for downstream use (Flutter subprocess launch, manual smoke testing) without requiring a local build.

---

## Decision

Use a single GitHub Actions workflow with two sequenced jobs:

```
build-and-test  ──→  package
```

The `package` job only runs when `build-and-test` passes. There is no point producing an artifact from a broken build.

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
      - ".github/workflows/ci.yml"
  pull_request:
    branches: ["main"]
    paths:
      - "backend/loom/**"
      - ".github/workflows/ci.yml"
```

The `paths` filter restricts runs to changes inside `backend/loom/` or the workflow file itself. A documentation-only commit at the repo root does not trigger a build.

---

### Job 1: `build-and-test`

| Step | Command | Purpose |
|---|---|---|
| Checkout | `actions/checkout@v4` | Full source tree |
| Set up Java | `actions/setup-java@v4` (Temurin 21) | Matches local dev JVM; cache keyed to wrapper properties |
| Compile | `./gradlew compileJava compileTestJava` | Fail fast on compilation errors before running tests |
| Test | `./gradlew test` | Runs JUnit 5 suite — `LocalServerBVT` (SSE) and `StorageBVT` (SQLite persistence) |
| Upload test report | `actions/upload-artifact@v4` | HTML test report, 7-day retention, uploaded even on failure |
| Build shadow JAR | `./gradlew shadowJar` | Fat JAR built in the same job — reuses the already-compiled classes, no second compile |
| Upload JAR (staging) | `actions/upload-artifact@v4` | Staging artifact, 1-day retention — only needs to survive until the `package` job |

The test report is uploaded with `if: always()` — it is most useful when tests fail, which is precisely when it would otherwise be skipped.

`shadowJar` runs after `test` in the same job so Gradle reuses the incremental build cache for compiled classes. This avoids the prior pattern where the `package` job checked out the source again and ran a full recompile.

---

### Job 2: `package`

| Step | Command | Purpose |
|---|---|---|
| Download JAR | `actions/download-artifact@v4` | Fetches `loom-engine-staging` produced by job 1 |
| Upload JAR | `actions/upload-artifact@v4` | Publishes as `loom-engine` with 30-day retention |

The `package` job contains no build steps — it promotes the already-verified artifact to a longer-lived artifact name. No recompilation; no second Java install.

---

## Key Decisions

### `paths` filter

Without it, every commit to any file in the monorepo triggers the Java build. The filter limits runs to changes that can actually affect the backend — saving time and Actions minutes. Files under `backend/loom/**` include source, Gradle config, `docker-compose.yml`, and the SQLite tooling container.

### Gradle daemon disabled (`--no-daemon`)

GitHub Actions runners are ephemeral containers that are torn down after each job. A Gradle daemon started in one step would be killed between jobs anyway, and keeping it alive within a job wastes memory. `--no-daemon` eliminates daemon lifecycle overhead.

### Separate compile and test steps

Running `compileJava compileTestJava` before `test` means compilation errors produce a distinct, clearly labelled step failure rather than being buried inside the test runner output. The cost is one extra Gradle invocation; the benefit is a faster, more readable failure signal.

### `shadowJar` in `build-and-test`, not `package`

Originally `package` checked out the repo again and ran `./gradlew shadowJar`, which triggered a full recompile. Moving `shadowJar` into `build-and-test` (after `test`) means:
- Gradle reuses the compiled `.class` files already in `build/classes/` — `shadowJar` only needs to bundle them.
- The `package` job becomes a pure artifact-promotion step with no build tooling required.
- Total CI time is reduced by one full Gradle compile cycle.

### Gradle cache keyed to wrapper properties

`cache-dependency-path: backend/loom/gradle/wrapper/gradle-wrapper.properties` ensures the cache key rotates when the Gradle distribution version changes, preventing stale cache hits after a wrapper upgrade.

### Two jobs instead of one

Separating test from packaging keeps responsibilities clear and makes the `package` job reusable as a standalone trigger in future (e.g. tag-based releases). It also means a test failure does not waste time building and uploading a JAR that came from broken code.

### `working-directory` default

```yaml
defaults:
  run:
    working-directory: backend/loom
```

Every `run` step executes from the Gradle project root without a `cd` prefix in each step. Note: `working-directory` applies only to `run` steps — `uses` steps (artifact upload/download) always use repo-root-relative paths.

---

## Artifact Retention

| Artifact | Retention | Rationale |
|---|---|---|
| `test-report` | 7 days | Needed only for debugging a recent failure |
| `loom-engine-staging` | 1 day | Temporary hand-off between `build-and-test` and `package` jobs |
| `loom-engine` | 30 days | Long enough for manual smoke tests and Flutter subprocess integration work |

---

## What This Does Not Cover

- **Deployment** — the JAR is produced as an artifact but not pushed anywhere. A separate release workflow (triggered on tag) would handle publishing.
- **Version tagging** — `version = "1.0-SNAPSHOT"` in `build.gradle.kts`; the shadow JAR is always named `loom-engine.jar` regardless of version.
- **Flutter CI** — the Flutter module has its own build pipeline; the JAR artifact is the integration point, not a shared workflow.
