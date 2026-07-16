# Roadmap

Iterations map to the [build order in ARCHITECTURE.md](ARCHITECTURE.md#12-build-order). Each iteration produces working, integrated software — not isolated components.

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

---

## Iteration 1 — Foundation

**Goal:** A running Java process that accepts HTTP connections, persists data, and can be started/stopped cleanly.

- [ ] [Java Core Engine: Project Scaffold + Javalin Server + SSE Manager](https://github.com/srijan-singh/loom/issues/2)
- [ ] [Domain Models + SQLite Persistence Layer](https://github.com/srijan-singh/loom/issues/3)

**Done when:** `curl localhost:{port}/health` returns 200 and all domain tables exist in the SQLite file.

---

## Iteration 2 — Single Agent

**Goal:** A fully working agent execution — load a skill, call an LLM, handle tool use, write a report.

- [ ] [LLM Gateway: Claude + GPT Providers](https://github.com/srijan-singh/loom/issues/4)
- [ ] [Agent Definition CRUD + Single Agent Execution](https://github.com/srijan-singh/loom/issues/6)

**Done when:** A single agent runs end-to-end via a REST call, uses an MCP tool, and writes a report to `workspace_knowledge`.

---

## Iteration 3 — Workflow Engine

**Goal:** Multiple agents executing in a structured graph with correct state transitions.

- [ ] [Workflow Engine: Chain Type](https://github.com/srijan-singh/loom/issues/7)
- [ ] [Workflow Engine: Supervisor Type](https://github.com/srijan-singh/loom/issues/5)

**Done when:** A supervisor workflow with 3+ agents runs correctly, handles a node failure gracefully, and the session reaches a terminal state with all reports written.

---

## Iteration 4 — Flutter Core UI

**Goal:** A usable app shell — onboarding, home dashboard, and full library management.

- [ ] [Flutter — App Shell, Onboarding & Home Dashboard](https://github.com/srijan-singh/loom/issues/8)
- [ ] [Flutter — Library Screens (Skills, MCPs, Agents)](https://github.com/srijan-singh/loom/issues/9)

**Done when:** A user can complete onboarding, add an API key, connect an MCP, create a skill, and define an agent — all without touching the backend directly.

---

## Iteration 5 — Workspace & Live Canvas

**Goal:** The centrepiece experience — watching a live workflow execute on the canvas.

- [ ] [Flutter — Workspace List, Detail Shell, Sessions & Knowledge Tabs](https://github.com/srijan-singh/loom/issues/10)
- [ ] [Flutter — Canvas Tab: Workflow Builder + Live SSE Execution](https://github.com/srijan-singh/loom/issues/11)

**Done when:** A user can build a workflow on the canvas, run it, watch nodes change state in real time, and inspect the session and knowledge tabs afterwards.

---

## Iteration 6 — Templates & MCP Display

**Goal:** One-click workflow assembly from a template based on the user's connected tools.

- [ ] [Java — Template Engine, MCP Client & Knowledge Store API](https://github.com/srijan-singh/loom/issues/12)
- [ ] [Flutter — Template Selection Screen & MCP Tools Display](https://github.com/srijan-singh/loom/issues/13)

**Done when:** A user with GitHub + Figma connected can select the UX Designer template and get a correctly assembled workflow with the right agents pre-wired.

---

## Iteration 7 — Hardening & Shipping

**Goal:** A product that is reliable enough to put in front of real users.

- [ ] [End-to-End Testing](https://github.com/srijan-singh/loom/issues/14)
- [ ] [Flutter Subprocess Lifecycle + Cross-Platform Packaging](https://github.com/srijan-singh/loom/issues/15)
- [ ] [UI/UX Polish Pass](https://github.com/srijan-singh/loom/issues/16)

**Done when:** The app installs and runs on macOS, Windows, and Linux. The full happy path passes an automated end-to-end test. No known crashes on the critical paths.

---
