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
| Test | `./gradlew test` | Runs JUnit 5 suite including `LocalServerBVT` |
| Upload report | `actions/upload-artifact@v4` | HTML test report, 7-day retention, uploaded even on failure |

The test report is uploaded with `if: always()` — it is most useful when tests fail, which is precisely when it would otherwise be skipped.

---

### Job 2: `package`

| Step | Command | Purpose |
|---|---|---|
| Checkout | `actions/checkout@v4` | Clean workspace for packaging |
| Set up Java | `actions/setup-java@v4` (Temurin 21) | Same JVM; Gradle cache reused from job 1 |
| Build JAR | `./gradlew shadowJar` | Produces `loom-engine.jar` via Shadow plugin |
| Upload JAR | `actions/upload-artifact@v4` | Fat JAR, 30-day retention |

---

## Key Decisions

### `paths` filter

Without it, every commit to any file in the monorepo triggers the Java build. The filter limits runs to changes that can actually affect the backend — saving time and Actions minutes.

### Gradle daemon disabled (`--no-daemon`)

GitHub Actions runners are ephemeral containers that are torn down after each job. A Gradle daemon started in one step would be killed between jobs anyway, and keeping it alive within a job wastes memory. `--no-daemon` eliminates daemon lifecycle overhead.

### Separate compile and test steps

Running `compileJava compileTestJava` before `test` means compilation errors produce a distinct, clearly labelled step failure rather than being buried inside the test runner output. The cost is one extra Gradle invocation; the benefit is a faster, more readable failure signal.

### Gradle cache keyed to wrapper properties

`cache-dependency-path: backend/loom/gradle/wrapper/gradle-wrapper.properties` ensures the cache key rotates when the Gradle distribution version changes, preventing stale cache hits after a wrapper upgrade.

### Two jobs instead of one

Separating compilation/test from packaging keeps responsibilities clear and makes the `package` job reusable as a standalone trigger in future (e.g. tag-based releases). It also means a test failure does not waste time building and uploading a JAR that came from broken code.

### `working-directory` default

```yaml
defaults:
  run:
    working-directory: backend/loom
```

Every `run` step executes from the Gradle project root without a `cd` prefix in each step. Keeps step definitions short and reduces copy-paste error if steps are reordered.

---

## Artifact Retention

| Artifact | Retention | Rationale |
|---|---|---|
| `test-report` | 7 days | Needed only for debugging a recent failure |
| `loom-engine` | 30 days | Long enough for manual smoke tests and Flutter subprocess integration work |

---

## What This Does Not Cover

- **Deployment** — the JAR is produced as an artifact but not pushed anywhere. A separate release workflow (triggered on tag) would handle publishing.
- **Version tagging** — `version = "1.0-SNAPSHOT"` in `build.gradle.kts`; the shadow JAR is always named `loom-engine.jar` regardless of version.
- **Flutter CI** — the Flutter module has its own build pipeline; the JAR artifact is the integration point, not a shared workflow.
