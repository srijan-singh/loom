# Lessons Learned

A real-time journal of deviations, surprises, and course corrections. Updated whenever something diverges from [DECISIONS.md](DECISIONS.md), [ARCHITECTURE.md](ARCHITECTURE.md), or [ROADMAP.md](ROADMAP.md).

Each entry follows the format below so lessons are scannable and actionable.

---

## Entry format

```markdown
## LL-NNN — Short title

**Date:** YYYY-MM-DD
**Area:** Architecture | Decision | Roadmap | Process
**Related:** D-NNN or ARCHITECTURE.md#section or Iteration N

**What happened**
One paragraph describing the deviation or surprise.

**Why it happened**
Root cause — was it an assumption that proved wrong, an underestimate, a missing constraint?

**What changed**
What was updated (code, a decision entry, the roadmap, this document).

**Takeaway**
One sentence. The thing to remember next time.
```

---

<!-- Add new entries below, newest first -->

## LL-003 — WorkflowEngine grew beyond a single responsibility

**Date:** 2025-07-14
**Area:** Architecture
**Related:** ARCHITECTURE.md#4-java-module-structure

**What happened**
`WorkflowEngine` accumulated seven distinct responsibilities in a single 925-line class: async session guard, chain orchestration, supervisor orchestration, single-node execution with timeout (copy-pasted three times), supervisor response parsing, worker batch dispatch, and SSE broadcasting. The class was hard to navigate and the timeout/status pattern had no single owner.

**Why it happened**
The engine started small (chain only) and the supervisor execution path was added as a second private method alongside the first. Each new concern — worker dispatch, `parseSupervisorResponse`, `buildSupervisorContext` — was appended as another private method rather than evaluated as a separate responsibility. The class grew by accretion with no forcing function to split it.

**What changed**
`WorkflowEngine` is being split into four focused classes. `WorkflowEngine` becomes the entry point only (~80 lines). `ChainExecutor` owns the chain loop. `SupervisorExecutor` owns the supervisor loop and all its helpers. `NodeRunner` owns the single-node submit→timeout→status contract — eliminating the three duplicated blocks. `ARCHITECTURE.md §4` updated to reflect the new structure.

**Takeaway**
When a second execution mode is added to an orchestrator, extract a dedicated executor class for it immediately rather than appending private methods to the existing one.

## LL-002 — No code style enforcement led to structural drift across the codebase

**Date:** 2026-09-21
**Area:** Process
**Related:** `26064f9` (#25)

**What happened**
With no automated style or structural enforcement, the codebase had accumulated inconsistent formatting, redundant Lombok annotations (`@Getter + @Setter + @EqualsAndHashCode` instead of `@Data`), star imports, inline fully-qualified class names, and complexity violations. Fixing it required touching nearly every Java file in a single large PR.

**Why it happened**
Style conventions existed informally but were never enforced by the build. AI-generated code in particular defaulted to verbose Lombok patterns and FQCNs in method bodies. Without a failing build gate, violations accumulated silently.

**What changed**
Spotless (Google Java Format AOSP) and Checkstyle were added to the Gradle build. `CODE_STYLE.md` was written to document every enforced rule and its rationale. All existing violations were fixed. The build now fails on any new structural violation; formatting is auto-fixable via `./gradlew spotlessApply`.

**Takeaway**
Add Spotless and Checkstyle to the build from day one — retrofitting them onto an existing codebase costs a large, noisy PR with no functional value.

## LL-001 — Repository layer had no shared base, duplicating JDBC boilerplate eight times

**Date:** 2026-07-28
**Area:** Architecture
**Related:** `c6ab2eb` (#20), ARCHITECTURE.md#4-java-module-structure

**What happened**
Each of the eight repositories (`SkillRepository`, `AgentRepository`, `SessionRepository`, etc.) independently implemented `findById`, `findAll`, `delete`, and JSON serialisation helpers using copy-pasted JDBC code. A bug fix or signature change had to be applied to all eight files.

**Why it happened**
Repositories were built one at a time as domain models were added. The first repository established the pattern and each subsequent one copied it. No base class was introduced at the time because it wasn't yet obvious the pattern would repeat.

**What changed**
`BaseRepository<T>` was introduced, providing `findById`, `findAll`, `delete`, a shared `ObjectMapper`, and JSON helpers (`toJsonList` / `fromJsonList` / `toJsonMap` / `fromJsonMap`). All eight repositories were refactored to extend it. `initialSchema.sql` was extracted from `DatabaseManager` into `src/main/resources/db/schema/`.

**Takeaway**
As soon as the second repository is written, extract a `BaseRepository` — the duplication is obvious at that point and cheap to fix; waiting until eight copies exist makes it a large, risky refactor.