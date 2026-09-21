# Loom Code Style Guide

Every rule in this document is enforced automatically by the build.
Run `./gradlew check` to validate all of them at once.

---

## Toolchain

| Tool | Version | Responsibility | Auto-fix? |
|---|---|---|---|
| **Spotless** (Google Java Format AOSP) | 7.0.4 / GJF 1.25.2 | Whitespace, indentation, import order, unused imports, licence header | ✅ `./gradlew spotlessApply` |
| **Checkstyle** | 10.21.4 | Structure, complexity, naming, visibility, coupling | ❌ Must be fixed manually |

The two tools are complementary and non-overlapping by design:
Spotless auto-fixes formatting; Checkstyle fails the build on structural issues that require a developer decision.

---

## 1. Formatting (Spotless — auto-fixed)

- **Indentation**: 4 spaces (AOSP style). No tabs.
- **Line endings**: Unix (`\n`), single newline at EOF.
- **Import order**: static imports first, then all others alphabetically. No sub-groups.
- **Unused imports**: removed automatically on every `spotlessApply`.
- **Licence header**: Apache 2.0 header enforced on every `.java` file.

> To fix all formatting violations at once: `./gradlew spotlessApply`

---

## 2. Lombok conventions

**Rule: always prefer `@Data` over `@Getter + @Setter + @EqualsAndHashCode` on the same class.**

`@Data` is exactly `@Getter + @Setter + @EqualsAndHashCode + @ToString` — using the three
individual annotations instead is redundant noise and omits `@ToString`, which means
`Object.toString()` is silently used instead of a useful representation.

```java
// ✅ correct
@Data
public class Session { ... }

// ❌ wrong — verbose, inconsistent, missing @ToString
@Getter
@Setter
@EqualsAndHashCode
public class Session { ... }
```

**Exceptions:**
- Use `@Getter` alone on immutable value objects with no setters (e.g. `ExecutionPlan`, `SupervisorExecutionPlan`).
- Use `@Data @NoArgsConstructor` when Jackson deserialisation requires a no-arg constructor (e.g. `SupervisorResponse`).
- Use `@Data @Builder` for builder-pattern DTOs (e.g. `LLMRequest`, `WorkflowEvent`).

**Why Checkstyle cannot enforce this:**
Checkstyle has no knowledge of Lombok's semantic equivalences. This convention must be
enforced via code review. AI-generated code is particularly prone to using the verbose form.

---

## 3. Imports (Checkstyle — build fails)

| Rule | What it catches |
|---|---|
| `AvoidStarImport` | `import com.loom.domain.*` — hides what is actually used |
| `UnusedImports` | Any import that is no longer referenced |
| `RedundantImport` | Duplicate imports of the same type |

**Fully-qualified class names in method bodies are banned.** If a type is used in a method,
add an import at the top of the file. Using `java.util.ArrayList` inline instead of importing
`ArrayList` is the specific pattern this rule catches.

```java
// ✅ correct
import java.util.ArrayList;
List<LLMMessage> history = new ArrayList<>();

// ❌ wrong — FQCN in method body
java.util.List<com.loom.llm.LLMMessage> history = new java.util.ArrayList<>();
```

---

## 4. Complexity (Checkstyle — build fails)

### Cyclomatic Complexity

Counts the number of decision points in a method (each `if`, `else`, `for`, `while`,
`case`, `catch`, `&&`, `||`, ternary) plus 1.

| Range | Interpretation |
|---|---|
| 1–10 | Safe: easy to read and test |
| 11–15 | Acceptable with thorough tests |
| 16–25 | Warning: consider decomposing |
| 26–35 | Hard limit: **build fails** above 35 |

**Current known-high methods (tracked tech debt):**

| Method | CC | Notes |
|---|---|---|
| `GraphResolver.resolveChain` | 30 | DAG validation + topological sort |
| `WorkflowEngine.runSupervisor` | 32 | Supervisor orchestration loop |

These methods exist above the "warning" threshold intentionally. New code must not exceed 35.

### NPath Complexity

Counts acyclic execution paths through a method (exponential). The ceiling is set very high
(10,000,000) because the existing orchestrator methods have astronomical NPath values that
reflect genuine algorithmic depth, not sloppiness.

**Known high-NPath methods (tech debt, not to be replicated):**

| Method | NPath |
|---|---|
| `GraphResolver.resolveChain` | ~8,626,176 |
| `GraphResolver.resolveSupervisor` | ~673,920 |
| `WorkflowEngine.runSupervisor` | ~2,796,752 |

These numbers mean these methods cannot be fully unit-tested via branch coverage alone.
When refactoring these methods, decompose them and bring NPath below 200.

---

## 5. Size limits (Checkstyle — build fails)

| Metric | Limit | Rationale |
|---|---|---|
| File length | 900 lines | `WorkflowEngine.java` is 823 lines today |
| Method length | 220 lines | `runSupervisor` is ~212 lines today |
| Parameter count | 12 | Large constructors from manual DI pattern |

---

## 6. Coupling (Checkstyle — build fails)

| Metric | Limit | Rationale |
|---|---|---|
| `ClassFanOutComplexity` | 40 | `WorkflowEngine` references 36 distinct types |
| `ClassDataAbstractionCoupling` | 10 | Max distinct classes *instantiated* inside one class |

`Main.java` is suppressed from `ClassDataAbstractionCoupling` with
`@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")` because it is the
**composition root** — its job is to instantiate every service and repository.

---

## 7. Visibility (Checkstyle — build fails)

All fields must be `private`. Exceptions require an explicit suppression with a comment:

```java
// ✅ Jackson DTO — public fields required for deserialisation
@SuppressWarnings("checkstyle:VisibilityModifier")
public static final class CreateRequest {
    public String name;
}

// ✅ Package-private test hook — intentional
@SuppressWarnings("checkstyle:VisibilityModifier")
long nodeTimeoutOverride = -1L;
```

Never suppress `VisibilityModifier` without a comment explaining why.

---

## 8. Coding practices (Checkstyle — build fails)

| Rule | What it catches |
|---|---|
| `EqualsHashCode` | Manually overriding `equals()` without `hashCode()` |
| `EqualsAvoidNull` | `variable.equals("literal")` — string literal must be on the left |
| `HiddenField` | Local variable or parameter shadowing a field |
| `MissingSwitchDefault` | `switch` without a `default` branch |
| `FallThrough` | Switch case falling through without a comment |
| `SimplifyBooleanExpression` | `if (b == true)` → `if (b)` |
| `SimplifyBooleanReturn` | `return x == true` → `return x` |
| `StringLiteralEquality` | `str == "literal"` comparisons |

---

## 9. Suppressing a rule

Always prefer fixing the violation. If suppression is genuinely necessary:

1. Use `@SuppressWarnings("checkstyle:<RuleName>")` on the smallest possible scope.
2. Add a comment on the same or preceding line explaining why.
3. Never suppress on a class when the violation is in one method.

```java
// ✅ narrow suppression with explanation
@SuppressWarnings("checkstyle:VisibilityModifier")  // Jackson needs public fields
public String prompt;

// ❌ too broad — suppresses everything on the class
@SuppressWarnings("checkstyle:all")
public class MyClass { ... }
```

---

## 10. ObjectMapper instances

Each class that needs JSON serialisation instantiates its own `new ObjectMapper()`.
This is consistent with the project's no-DI-framework pattern. Do not introduce a shared
singleton unless it is proven necessary — `ObjectMapper` is thread-safe after construction
but configuration changes are not.
