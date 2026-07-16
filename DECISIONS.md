# Decisions

This document records the key product and technical decisions made for Loom. Each entry captures the context, the options considered, and the reasoning behind the choice. It is a living document — decisions are never deleted, only superseded.

---

## D-001 — Why build another AI project?

**Status:** Accepted

**Context**  
Existing multi-agent orchestration tools (AutoGen, CrewAI, LangGraph, etc.) are either SaaS (data leaves the machine, vendor lock-in, opaque internals) or developer-only libraries (no UI, steep learning curve, not usable by non-technical people).

**Decision**  
Build a local-first desktop application that lets anyone — regardless of technical background — compose and run AI agent workflows using their own AI keys and their own tools, with full transparency into what is happening at every step.

**Consequences**  
- No network dependency beyond the user's own LLM and MCP providers.
- We own the full execution environment, which makes auditability and determinism achievable.
- Distribution complexity (packaging for macOS, Windows, Linux) is our problem to solve.

---

## D-002 — Backend: Java

**Status:** Accepted

**Context**  
The core engine needs to be robust, long-running, and easy to reason about. It will handle concurrent agent executions, SQLite persistence, HTTP serving, and LLM streaming.

**Decision**  
Use Java for the backend subprocess.

**Rationale**
- Battle-tested concurrency primitives (threads, executors)
- Strong ecosystem for HTTP servers (Javalin), SQLite (JDBC), and JSON
- Zero learning curve for the primary author
- Predictable performance and straightforward debugging

**Alternatives considered**
- *Node.js* — fast to write, but async callback complexity at scale; weaker typing
- *Python* — popular in the AI space, but packaging and distribution complexity across platforms; CPU-bound parallelism is also weaker for any future compute-intensive tasks
- *Go* — compelling, but steeper learning curve and smaller ecosystem for HTTP + DB

---

## D-003 — UI: Flutter

**Status:** Accepted

**Context**  
The app must ship on macOS, Windows, and Linux from a single codebase. The UI needs a canvas (workflow graph), live streaming output panels, and standard CRUD screens.

**Decision**  
Use Flutter for the UI layer.

**Rationale**
- Write once, deploy anywhere — one codebase for all desktop targets
- Mature widget system capable of building a custom canvas
- Good process management APIs for spawning the Java subprocess
- Strong performance for UI-heavy screens

**Alternatives considered**  
- *Electron + React* — larger bundle size, higher memory footprint, slower startup
- *Tauri + React* — compelling, but Rust backend is a learning curve; Flutter canvas ecosystem is stronger

---

## D-004 — Database: SQLite

**Status:** Accepted

**Context**  
All data (skills, agent definitions, workflows, session history, workspace knowledge) must be persisted locally. The user must be able to back it up trivially.

**Decision**  
Use a single SQLite file on the user's machine.

**Rationale**
- Zero server setup
- Single file = trivial backup, copy, and restore
- More than sufficient for single-user local workloads
- JDBC driver is a first-class Java citizen

**Alternatives considered**  
- *Embedded PostgreSQL* — overkill for single-user; heavy to package
- *H2* — Java-native but less portable and less familiar for inspection with external tools

---

## D-005 — System design philosophy: explicit, traceable, deterministic

**Status:** Accepted

**Context**  
AI systems are notoriously hard to debug. Errors can be silent, context-dependent, and non-reproducible. The system design must fight this directly.

**Decision**  
The entire system is designed around four explicit rules:

1. **Single responsibility per agent** — each agent has one skill and one job; failures are localised
2. **Isolated context per execution** — each agent execution receives its own scoped context; no shared mutable state between agents
3. **Explicit, human-readable contracts** — the workflow JSON, SSE event payloads, and agent reports are all designed to be readable without tooling
4. **System adapts to the problem** — templates and conditional workflow assembly mean the system configures itself to the user's available tools, rather than forcing the user to match a fixed topology

**Consequences**  
- Every execution produces a written report (the "death report") that can be inspected after the fact
- The SSE event stream gives full real-time visibility into what every agent is doing
- Debugging a failed session means reading a report, not parsing logs

---

## D-006 — Flutter/Java communication: REST + SSE

**Status:** Accepted

**Context**  
Flutter (UI) and Java (engine) run as separate processes on the same machine. They need a communication channel for two distinct patterns: request/response (CRUD) and push (live execution updates).

**Decision**  
- **REST over localhost** for all CRUD operations (skills, agents, workflows, workspaces)
- **Server-Sent Events (SSE)** for the live execution event stream

**Rationale**
- REST is stateless, easy to test, and maps cleanly to the CRUD surface
- SSE is simpler than WebSockets for a unidirectional push channel; it uses a long-lived HTTP connection and avoids the WebSocket upgrade, making it trivially compatible with standard HTTP tooling
- Both use standard HTTP — no custom serialization, no binary protocol, easy to inspect with curl

**Alternatives considered**  
- *WebSockets* — bidirectional, but Flutter → Java commands fit REST better; SSE is sufficient for engine → Flutter events
- *gRPC* — type-safe and efficient, but adds proto compilation complexity and tooling overhead

---

## D-007 — Agent output: written reports saved to workspace knowledge

**Status:** Accepted

**Context**  
In a multi-agent workflow, later agents need context from earlier agents. Passing the full output of every agent through the prompt chain would exceed context windows and create tight coupling between agents.

**Decision**  
Every agent, upon completion, writes a structured "death report" (task, tools called, result, status) to the workspace's `workspace_knowledge` store. Subsequent agents fetch only the reports relevant to their task, scoped to the current workspace.

**Rationale**
- Keeps individual prompt sizes manageable
- Decouples agents — they communicate through an artifact store, not direct message passing
- Reports are human-readable and inspectable in the Knowledge tab
- Natural audit trail

---
