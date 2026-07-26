# ADR: Domain Model and SQLite Persistence Layer

**Status:** Accepted  
**Date:** 2026-07-26  
**Scope:** `com.loom.domain`, `com.loom.storage`, `com.loom.storage.repository`

---

## Context

The Loom engine needs a persistence layer to store and retrieve all core entities — skills, MCP connections, agent definitions, workflow definitions, workspaces, sessions, agent executions, and workspace knowledge. Every subsequent issue (CRUD routes, execution engine) reads and writes through this layer; nothing else touches the database directly.

The engine runs as an embedded JVM process launched by the Flutter host. It must work on macOS, Linux, and Windows without requiring a separate database server process.

---

## Decision

Use **SQLite via plain JDBC** (`sqlite-jdbc` driver) with **no ORM**.

---

## Why SQLite

| Concern | SQLite | PostgreSQL / MySQL |
|---|---|---|
| Deployment | Zero — embedded in the JAR | Requires a running server |
| Cross-platform | Native binaries bundled by `sqlite-jdbc` | Separate install |
| Concurrency | Single-writer; sufficient for a local engine | Multi-writer overkill |
| Tooling | Any SQLite browser for debugging | Heavier admin tooling |

The engine is a local subprocess — one user, one process, one write at a time. SQLite's single-writer model is a perfect fit.

---

## Why No ORM

| Concern | Plain JDBC | Hibernate / JOOQ |
|---|---|---|
| Dependencies | One JAR (`sqlite-jdbc`) | Multi-JAR, classpath conflicts |
| Transparency | SQL is explicit and auditable | Generated queries are opaque |
| Startup | Immediate | Reflection-heavy bootstrap |
| Complexity | CRUD + 5 extra queries; fits in ~100 lines per repo | Annotation scanning, entity proxies |

The schema is fixed, simple, and purpose-built. ORM abstraction adds more weight than it removes for this scope.

---

## Architecture

### `DatabaseManager`

- Opens (or creates) the SQLite file at `LOOM_DB_PATH` env var; defaults to `./loom.db`.
- Runs `CREATE TABLE IF NOT EXISTS` for all 9 tables on startup — idempotent, safe to call on every boot.
- Exposes `getConnection()` returning a new `Connection` per call; SQLite's connection overhead is negligible for a local single-writer workload.
- Package-private `DatabaseManager(String dbPath)` constructor for test injection — avoids polluting `System.getenv` in tests.

### Domain models (`com.loom.domain`)

Plain POJOs — no framework annotations. ID is `UUID.randomUUID().toString()` generated in the no-arg constructor so callers never have to set it explicitly.

```
Skill               WorkflowDefinition (→ nodes[], edges[])
MCPConnection       Workspace
AgentDefinition     Session
WorkflowNode        AgentExecution
WorkflowEdge        WorkspaceKnowledge
```

Enums:

```
WorkflowType        CHAIN | SUPERVISOR
WorkflowCreatedBy   TEMPLATE | USER
NodeType            SUPERVISOR | WORKER | START | END
EdgeCondition       ON_SUCCESS | ON_FAILURE | ALWAYS
MCPStatus           CONNECTED | DISCONNECTED
SessionStatus       PENDING | RUNNING | COMPLETED | FAILED
AgentExecutionStatus PENDING | RUNNING | COMPLETED | FAILED
```

### Repositories (`com.loom.storage.repository`)

Each repository receives a `DatabaseManager` via constructor injection and owns all SQL for its table. Public surface:

```
save(T)              — INSERT OR REPLACE (upsert via ON CONFLICT DO UPDATE)
findById(String)     — SELECT by primary key → Optional<T>
findAll()            — SELECT * → List<T>
delete(String)       — DELETE by primary key
```

Additional query methods:

| Repository | Extra |
|---|---|
| `SkillRepository` | `findByTag(String)` |
| `AgentRepository` | `findBySkillId(String)` |
| `WorkflowRepository` | `findByType(WorkflowType)` |
| `SessionRepository` | `findByWorkspaceId(String)`, `findByStatus(SessionStatus)` |
| `WorkspaceRepository` | `findByWorkflowId(String)` — JOIN on `workspace_workflows` |

---

## Serialization Conventions

### `tags` / `allowedMcpIds` — comma-separated TEXT

```
["search", "web", "research"]  ←→  "search,web,research"
```

Simple, queryable with `LIKE`, and avoids a JSON dependency for scalar string lists.

`findByTag` uses a wrapped-comma trick to avoid false-prefix matches:

```sql
WHERE (',' || tags || ',') LIKE '%,web,%'
```

This correctly matches `"web"` inside `"search,web,research"` without matching `"webdev"`.

### `MCPConnection.config` — JSON TEXT

Serialized with Jackson `ObjectMapper` as a `Map<String, Object>`. Config structures are heterogeneous and nested; JSON is the natural representation.

### `WorkflowDefinition.graph` — JSON TEXT

Nodes and edges are serialized together as:

```json
{
  "nodes": [ { "id": "...", "label": "...", "nodeType": "WORKER", ... } ],
  "edges": [ { "id": "...", "fromNodeId": "...", "condition": "ON_SUCCESS" } ]
}
```

Stored in a single `graph` column rather than two join tables because:
- Nodes and edges are always loaded together (the graph is the unit of work)
- The graph shape changes with the workflow; a join table would require cascading deletes
- Jackson deserialization into typed `WorkflowNode` / `WorkflowEdge` lists is straightforward

### Nullable `completedAt` (Session, AgentExecution)

- Write: `ps.setNull(index, Types.INTEGER)` when `null`
- Read: `rs.getLong(col)` then `rs.wasNull()` guard before setting the field

---

## Schema

```sql
CREATE TABLE IF NOT EXISTS skills (
  id TEXT PRIMARY KEY, name TEXT, description TEXT,
  content TEXT, tags TEXT, created_at INTEGER, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS mcp_connections (
  id TEXT PRIMARY KEY, name TEXT, type TEXT,
  config TEXT, status TEXT, created_at INTEGER
);
CREATE TABLE IF NOT EXISTS agent_definitions (
  id TEXT PRIMARY KEY, name TEXT, role_description TEXT,
  skill_id TEXT, allowed_mcp_ids TEXT, created_at INTEGER, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS workflow_definitions (
  id TEXT PRIMARY KEY, name TEXT, type TEXT, created_by TEXT,
  graph TEXT, created_at INTEGER, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS workspaces (
  id TEXT PRIMARY KEY, name TEXT, description TEXT, created_at INTEGER
);
CREATE TABLE IF NOT EXISTS workspace_workflows (
  workspace_id TEXT, workflow_definition_id TEXT
);
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY, workspace_id TEXT, workflow_definition_id TEXT,
  status TEXT, started_at INTEGER, completed_at INTEGER
);
CREATE TABLE IF NOT EXISTS agent_executions (
  id TEXT PRIMARY KEY, session_id TEXT, node_id TEXT,
  agent_definition_id TEXT, status TEXT, input_context TEXT,
  output TEXT, report TEXT, started_at INTEGER, completed_at INTEGER
);
CREATE TABLE IF NOT EXISTS workspace_knowledge (
  id TEXT PRIMARY KEY, workspace_id TEXT, source_execution_id TEXT,
  title TEXT, content TEXT, tags TEXT, created_at INTEGER
);
```

Timestamps are stored as `INTEGER` (Unix epoch milliseconds) — unambiguous, sortable, and avoids SQLite's text-date parsing quirks.

---

## Testing

`StorageBVT` — single end-to-end test method (`fullStorageRoundTrip`) that exercises all 8 repositories in sequence using a real SQLite file in a JVM temp directory:

1. Save and round-trip each entity through `save → findById`
2. Assert list fields (`tags`, `allowedMcpIds`) deserialize correctly as `List<String>`
3. Assert `WorkflowDefinition` graph (3 nodes, 2 edges) serializes to JSON and deserializes back without data loss — all node types, positions, agent references, and edge conditions preserved
4. Assert nullable `completedAt` is `null` before completion, non-null after
5. Assert all extra query methods (`findByTag`, `findBySkillId`, `findByType`, `findByWorkspaceId`, `findByStatus`)
6. Assert `findAll()` returns exactly 1 for each repository
7. Assert `delete()` removes the record; subsequent `findById` returns `Optional.empty()`

The database file is deleted in `@AfterAll` — no cleanup required between runs.

---

## Alternatives Considered

### Hibernate / Jakarta Persistence

Rejected: adds ~10 JAR dependencies, requires annotation processing, has a slow reflective bootstrap, and generates SQL that is hard to audit. The query surface here is small enough that plain JDBC is cleaner.

### H2 (in-memory)

Rejected in favour of SQLite: H2 in-memory would be convenient for tests but diverges from the production engine (different SQL dialect, no persistent file). SQLite with a temp file gives identical behaviour in tests and production.

### One connection pool (HikariCP)

Not needed: the engine is single-threaded for writes, and repositories open and close a connection per operation. Connection overhead for SQLite is sub-millisecond. Introducing a pool adds a dependency and lifecycle management for no measurable benefit.
