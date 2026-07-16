# Architecture

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Guiding Principles](#2-guiding-principles)
3. [Component Breakdown](#3-component-breakdown)
4. [Java Module Structure](#4-java-module-structure)
5. [Data Models](#5-data-models)
6. [State Machines](#6-state-machines)
7. [Agent Execution Flow](#7-agent-execution-flow)
8. [SSE Event Stream](#8-sse-event-stream)
9. [Template Engine](#9-template-engine)
10. [Database Schema](#10-database-schema)
11. [UI Information Architecture](#11-ui-information-architecture)
12. [Build Order](#12-build-order)

---

## 1. System Overview

Loom is a local-first desktop application. The Flutter shell manages the process lifecycle and renders the UI. All business logic runs inside a Java subprocess, which the Flutter layer communicates with over HTTP (REST + SSE). A single SQLite file on the user's machine provides persistence.

The Java server binds exclusively to the loopback interface (`127.0.0.1`). The listening port is chosen at startup and handed to the Flutter shell via stdout (or a shared environment variable), so Flutter always knows the exact ephemeral address. Each launch generates a per-launch secret token; Flutter includes this token on every REST request and SSE connection. The server rejects any request that omits a valid token, preventing other local processes from accessing these routes.

```text
┌─────────────────────────────────────────────────────────┐
│                    Desktop Application                  │
│                                                         │
│  ┌─────────────────┐         ┌─────────────────────┐    │
│  │   Flutter UI    │◄──SSE───│   Java Core Engine  │    │
│  │   (Shell/View)  │──REST──►│   (Subprocess)      │    │
│  └─────────────────┘         └──────────┬──────────┘    │
│                                         │               │
│                               ┌─────────▼──────────┐    │
│                               │   SQLite Database  │    │
│                               └────────────────────┘    │
└─────────────────────────────────────────────────────────┘
         │                              │
         │ outbound only                │ outbound only
         ▼                              ▼
   LLM Providers                  MCP Servers
   (Claude/GPT)                (GitHub/Figma/etc)
```

Both external connections (LLM providers and MCP servers) are **outbound only**. No inbound network access is required; no user data is sent to Loom's servers because there are none.

---

## 2. Guiding Principles

| # | Principle | Meaning |
|---|-----------|---------|
| 1 | **Local first** | No user data is sent to Loom-operated servers; configured LLM providers and MCP servers may receive workspace context and agent prompts |
| 2 | **Bring your own AI** | API keys are stored locally; Loom never sees them |
| 3 | **Modular agents** | One agent, one skill, one job |
| 4 | **Audit everything** | Every execution is fully traceable |
| 5 | **Simple substrate** | No magic — all wiring is explicit |

---

## 3. Component Breakdown

### Flutter (UI Layer)

**Responsibilities**
- Render UI
- Manage Java subprocess lifecycle (start, stop, health)
- Subscribe to SSE stream for live execution updates
- Issue REST calls for all CRUD operations
- Manage local app state (navigation, selection)
- Package and ship cross-platform

**Does NOT**
- Contain business logic
- Talk to LLMs directly
- Know about workflow internals

### Java Core Engine

**Responsibilities**
- All business logic
- Workflow orchestration
- Agent execution
- LLM communication
- MCP tool invocation
- Knowledge store management
- SSE event emission
- SQLite persistence

**Does NOT**
- Know about UI concerns
- Manage its own process lifecycle
- Handle app-level navigation

### SQLite

**Responsibilities**
- Persist all definitions (skills, agents, workflows)
- Persist all session history
- Persist workspace knowledge
- Single file on the user's machine — trivially backed up

---

## 4. Java Module Structure

```text
com.loom
 ├── Main.java                    → Entry point, wires everything
 │
 ├── transport
 │    ├── LocalServer.java        → Javalin setup, port management
 │    ├── SSEManager.java         → Manages open SSE connections
 │    └── routes
 │         ├── AgentRoutes.java
 │         ├── WorkflowRoutes.java
 │         ├── SkillRoutes.java
 │         ├── MCPRoutes.java
 │         ├── SessionRoutes.java
 │         └── WorkspaceRoutes.java
 │
 ├── engine
 │    ├── WorkflowEngine.java     → Orchestrates workflow execution
 │    ├── GraphResolver.java      → Parses workflow JSON into execution plan
 │    ├── StateManager.java       → Manages session + node states
 │    ├── AgentRuntime.java       → Executes a single agent
 │    ├── ContextBuilder.java     → Assembles prompt for agent
 │    └── ReportWriter.java       → Writes agent death report
 │
 ├── llm
 │    ├── LLMGateway.java         → Interface
 │    ├── LLMRequest.java         → Shared request model
 │    ├── LLMResponse.java        → Shared response model
 │    ├── ClaudeProvider.java     → Claude implementation
 │    └── GPTProvider.java        → GPT implementation
 │
 ├── mcp
 │    ├── MCPClient.java          → Interface
 │    ├── MCPToolCall.java        → Tool call model
 │    ├── MCPToolResult.java      → Tool result model
 │    └── MCPConnectionManager.java → Manages active connections
 │
 ├── template
 │    ├── TemplateEngine.java     → Evaluates template conditions
 │    ├── TemplateLoader.java     → Loads built-in + custom templates
 │    ├── WorkflowAssembler.java  → Builds workflow JSON from template
 │    └── conditions
 │         ├── MCPCondition.java
 │         └── SkillCondition.java
 │
 ├── domain
 │    ├── Skill.java
 │    ├── MCPConnection.java
 │    ├── AgentDefinition.java
 │    ├── WorkflowDefinition.java
 │    ├── WorkflowNode.java
 │    ├── WorkflowEdge.java
 │    ├── Workspace.java
 │    ├── Session.java
 │    └── AgentExecution.java
 │
 ├── storage
 │    ├── DatabaseManager.java    → SQLite connection, migrations
 │    └── repository
 │         ├── SkillRepository.java
 │         ├── AgentRepository.java
 │         ├── WorkflowRepository.java
 │         ├── SessionRepository.java
 │         └── WorkspaceRepository.java
 │
 └── events
      ├── WorkflowEvent.java      → Event model
      └── EventType.java          → Enum of all event types
```

---

## 5. Data Models

### WorkflowDefinition

The internal DTO for a workflow. The canvas serializes to this format; the engine reads it.

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | |
| `name` | string | |
| `type` | enum | `CHAIN \| SUPERVISOR \| PARALLEL` |
| `createdBy` | enum | `TEMPLATE \| USER` |
| `nodes` | `Node[]` | See below |
| `edges` | `Edge[]` | See below |
| `metadata` | object | Arbitrary key-value store |

**Node fields:** `id`, `label`, `nodeType` (`SUPERVISOR \| WORKER \| START \| END`), `agentDefinitionId` (nullable), `position` `{x, y}`

**Edge fields:** `id`, `fromNodeId`, `toNodeId`, `condition` (`ON_SUCCESS \| ON_FAILURE \| ALWAYS`)

---

## 6. State Machines

### Session State Machine

```text
                    ┌─────────┐
                    │ CREATED │
                    └────┬────┘
                         │ run()
                    ┌────▼────┐
                    │ RUNNING │
                    └────┬────┘
          ┌──────────────┼──────────────┐
          │              │              │
     ┌────▼────┐   ┌─────▼─────┐   ┌────▼────┐
     │COMPLETED│   │  FAILED   │   │ PARTIAL │
     └─────────┘   └───────────┘   └─────────┘
```

`PARTIAL` indicates some agents completed successfully before a failure occurred.

### Agent Execution State Machine

```text
          ┌─────────┐
          │ WAITING │  ← dependencies not yet complete
          └────┬────┘
               │ dependencies resolved
          ┌────▼────┐
          │ QUEUED  │  ← ready to run, awaiting thread
          └────┬────┘
               │ thread assigned
          ┌────▼────┐
          │ RUNNING │
          └────┬────┘
     ┌─────────┼─────────┐
     │         │         │
┌────▼────┐ ┌──▼───┐ ┌───▼─────┐
│COMPLETED│ │FAILED│ │TIMED_OUT│
└────┬────┘ └──┬───┘ └───┬─────┘
     │         │         │
     └─────────▼─────────┘
          writes report
          to workspace
          then TERMINATED
```

---

## 7. Agent Execution Flow

The sequence inside `AgentRuntime.execute(agentDefinition, session, inputContext)`:

```text
1. Load skill markdown from storage

2. Fetch workspace context
      └── Previous agent reports relevant to this task
      └── Scoped to current workspace only

3. ContextBuilder.build()
      └── System prompt  = skill markdown
      └── User prompt    = task description + input context
      └── Available tools = agent's allowed MCPs

4. LLMGateway.send(request)
      └── Streams response back
      └── Emits SSE AGENT_TOKEN events to Flutter
      └── On tool_use response:
           └── MCPClient.call(tool, params)
           └── Result fed back to LLM
           └── Loop until no more tool calls

5. ReportWriter.write()
      └── What was the task
      └── What tools were called
      └── What was the result
      └── Status (success/failure)
      └── Saved to workspace_knowledge

6. Emit AGENT_COMPLETED via SSE
      └── StateManager updates session
      └── WorkflowEngine resolves next nodes
```

---

## 8. SSE Event Stream

Flutter opens a single SSE connection and reacts to all events on it. Every significant engine action emits an event.

### Event Types

| Event | Description |
|-------|-------------|
| `SESSION_STARTED` | A new session has begun |
| `SESSION_COMPLETED` | Session finished successfully |
| `SESSION_FAILED` | Session terminated with error |
| `NODE_WAITING` | Node is blocked on dependencies |
| `NODE_RUNNING` | Node's agent has started |
| `NODE_COMPLETED` | Node's agent finished successfully |
| `NODE_FAILED` | Node's agent failed |
| `AGENT_TOKEN` | Single streaming LLM output token |
| `AGENT_TOOL_CALL` | Agent is invoking an MCP tool |
| `AGENT_TOOL_RESULT` | MCP tool returned its result |
| `AGENT_REPORT_WRITTEN` | Death report saved to workspace |
| `WORKFLOW_STATE_CHANGE` | General workflow state update |

### Event Payload Shape

```text
eventType
sessionId
nodeId              (nullable)
agentExecutionId    (nullable)
timestamp
data                (event-specific payload)
```

---

## 9. Template Engine

Templates are JSON config files (not code). `TemplateEngine.assemble(templateId, userContext)` evaluates each template's conditions against the user's current MCP connections and skills to generate a `WorkflowDefinition` in the same format as a user-built workflow.

### Assembly Steps

```text
1. Load template definition (JSON config file)

2. Evaluate conditions against userContext
      └── available MCP connection types
      └── available skill names

3. For each passing condition
      └── Add node to workflow
      └── Add edges per template topology

4. Always add mandatory nodes (Supervisor, etc.)

5. Return WorkflowDefinition
      └── Feeds into the same WorkflowEngine
```

### Example: UX Persona Template

| Node | Condition |
|------|-----------|
| Supervisor | Always |
| ValidationAgent | Always |
| IssueReaderAgent | MCP type contains `GITHUB` or `JIRA` |
| DesignContextAgent | MCP type contains `FIGMA` or `CANVA` |
| Skill guardrail on Supervisor | Skills exist with tag `UX` |

---

## 10. Database Schema

```sql
skills
  id          TEXT PRIMARY KEY
  name        TEXT
  description TEXT
  content     TEXT    -- markdown body
  tags        TEXT    -- comma-separated
  created_at  INTEGER
  updated_at  INTEGER

mcp_connections
  id          TEXT PRIMARY KEY
  name        TEXT
  type        TEXT    -- GITHUB | FIGMA | FILESYSTEM | …
  config      TEXT    -- JSON blob (non-sensitive connection details only;
                      --   API credentials must NOT be stored here —
                      --   store them in the OS-backed secret store and
                      --   persist only a secret reference in this field.
                      --   The config column is safe to include in backups;
                      --   the secret store entry is not.)
  status      TEXT    -- CONNECTED | DISCONNECTED
  created_at  INTEGER

agent_definitions
  id                TEXT PRIMARY KEY
  name              TEXT
  role_description  TEXT
  skill_id          TEXT    -- FK → skills
  allowed_mcp_ids   TEXT    -- JSON array of mcp_connection ids
  created_at        INTEGER
  updated_at        INTEGER

workflow_definitions
  id          TEXT PRIMARY KEY
  name        TEXT
  type        TEXT    -- CHAIN | SUPERVISOR | PARALLEL
  created_by  TEXT    -- TEMPLATE | USER
  graph       TEXT    -- full workflow JSON
  created_at  INTEGER
  updated_at  INTEGER

workspaces
  id          TEXT PRIMARY KEY
  name        TEXT
  description TEXT
  created_at  INTEGER

workspace_workflows
  workspace_id            TEXT    -- FK → workspaces(id) ON DELETE CASCADE
  workflow_definition_id  TEXT    -- FK → workflow_definitions(id) ON DELETE CASCADE
  PRIMARY KEY (workspace_id, workflow_definition_id)

sessions
  id                      TEXT PRIMARY KEY
  workspace_id            TEXT
  workflow_definition_id  TEXT
  status                  TEXT
  started_at              INTEGER
  completed_at            INTEGER

agent_executions
  id                    TEXT PRIMARY KEY
  session_id            TEXT
  node_id               TEXT
  agent_definition_id   TEXT
  status                TEXT
  input_context         TEXT    -- what was passed in
  output                TEXT    -- what the agent produced
  report                TEXT    -- death report markdown
  started_at            INTEGER
  completed_at          INTEGER

workspace_knowledge
  id                    TEXT PRIMARY KEY
  workspace_id          TEXT
  source_execution_id   TEXT
  title                 TEXT
  content               TEXT    -- report content
  tags                  TEXT
  created_at            INTEGER
```

---

## 11. UI Information Architecture

```text
App
 ├── Onboarding (first launch only)
 │    ├── Welcome
 │    ├── API Key Setup
 │    └── First MCP Connection
 │
 ├── Home Dashboard
 │
 ├── Library
 │    ├── Skills
 │    ├── MCP Connections
 │    └── Agent Definitions
 │
 ├── Workspaces
 │    ├── Workspace List
 │    └── Workspace Detail
 │         ├── Canvas  (workflow builder + live execution)
 │         ├── Sessions (history)
 │         └── Knowledge (reports browser)
 │
 └── Settings
      ├── API Keys
      └── About
```

### Canvas Live Execution — Node & Edge States

| Element | State | Visual |
|---------|-------|--------|
| Node | `WAITING` | Grey, dimmed |
| Node | `QUEUED` | Grey, solid border |
| Node | `RUNNING` | Blue/green, pulsing animated border |
| Node | `COMPLETED` | Green with checkmark |
| Node | `FAILED` | Red with ✗ |
| Edge | `INACTIVE` | Grey dashed |
| Edge | `ACTIVE` | Animated flowing dots, coloured |
| Edge | `COMPLETED` | Solid coloured line |

---

## 12. Build Order

| Weeks | Milestone |
|-------|-----------|
| 1–2 | Subprocess model + LLM gateway + single skill call |
| 3–4 | Agent definition CRUD + single agent execution |
| 5–6 | Workflow engine (chain type only) |
| 7–8 | Supervisor workflow type |
| 9–10 | Flutter UI — library screens |
| 11–12 | Canvas + SSE live visualization |
| 13–14 | UX template + knowledge store |
| 15–16 | Polish, packaging, testing |
