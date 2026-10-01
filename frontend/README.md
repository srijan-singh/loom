# loom_ui

Flutter (macOS) front-end for the Loom AI-workforce desktop app.

## What's implemented

### Foundation
- **`main.dart`** — app entry point; resolves the bundled engine JAR path, boots
  `EngineProcessService`, wires Riverpod `ProviderScope` overrides, and mounts
  `LoomApp` with light/dark `ThemeData`.
- **`LoomTheme` / `LoomColors`** — full design-token system (light + dark palettes,
  spacing, radius, and size constants) as a `ThemeExtension`; matches the UX
  reference exactly.
- **`router.dart`** — `go_router`-based routing with onboarding redirect guard.
  All named routes defined in `Routes`.

### Services
| Service | Status |
|---|---|
| `EngineProcessService` | Launches the Java engine JAR as a subprocess; parses `LOOM_PORT` / `LOOM_TOKEN` from stdout; tees all stdout+stderr to `$TMPDIR/loom-engine.log`; 30 s startup timeout. |
| `ApiClient` | Thin Dio wrapper; injects `X-Loom-Token` on every request; resolves base URL from the live engine port or `LOOM_ENGINE_URL` dart-define; domain helpers for `/skills`, `/mcps`, `/agents`, `/sessions`, `/llm/test`, `/llm/models`, `/health`. |
| `SseService` | Connects to `GET /events`; exposes a broadcast `Stream<WorkflowEvent>`; auto-reconnects on error after 5 s. |
| `StorageService` | API key → `FlutterSecureStorage` (OS keychain); provider / model / onboarding flag → `SharedPreferences`. |

### Onboarding (no shell chrome)
- **Welcome screen** (`/onboarding/welcome`) — logo mark, tagline, body copy,
  primary CTA, fine-print, decorative woven-grid strip at the bottom edge.
- **API key screen** (`/onboarding/api-key`) — provider segment control
  (Claude / GPT), masked API-key field with inline validation, model dropdown
  (populated from `/llm/models` after a successful test; falls back to hardcoded
  list), "Test key" flow with five inline states (`idle / testing / valid /
  rejected / unreachable`), "Save and continue" persists to keychain and marks
  onboarding complete.
- **MCP setup screen** (`/onboarding/mcp-setup`) — stub; redirects straight to
  dashboard (not yet built).

### App shell
- **`AppShell`** — persistent 72 px left rail (Dashboard / Library / Workspaces
  + Settings pinned at the bottom); active-route highlight; engine status dot
  (green / amber / red).
- **Engine starting overlay** — animated indeterminate progress bar, shown over
  the full window while the JAR is booting.
- **Engine unreachable overlay** — warning state with "Retry" and "View logs"
  actions.

### Screens (inside the shell)
| Screen | Route | Status |
|---|---|---|
| Dashboard | `/dashboard` | Implemented — time-of-day greeting; stat cards (skills / MCPs connected / agents); new-user setup checklist when all counts are zero; recent sessions list with skeleton loaders and empty state; "New workspace" + "Use a template" quick-start buttons. |
| Library | `/library` | Stub — empty state placeholder. |
| Workspaces | `/workspaces` | Stub — empty state placeholder. |
| Settings | `/settings` | Stub — empty state placeholder. |
| Session detail | `/sessions/:id` | Stub — falls back to `DashboardScreen`. |
| Templates | `/templates` | Stub — falls back to `LibraryScreen`. |

### Shared components
| Component | Notes |
|---|---|
| `LoomButton` | 4 variants (primary / secondary / destructive / ghost); compact mode; loading spinner; disabled opacity. |
| `LoomTextField` | Labelled input with hint, error text, obscure toggle. |
| `SessionRow` | Single session list item with status dot, name, timestamp. |
| `StatCard` | Numeric count card with skeleton, error+retry, and data states. |
| `SkeletonLoader` | Animated shimmer placeholder (reused across screens). |
| `EmptyState` | Icon + headline + body + optional action button. |

### Assets
- `AppIcon` — generated from the UX-reference `#i-mark` SVG (3×3 weave grid,
  `#E8EAF8` background, `#3846B0` mark); all 7 required macOS sizes
  (16 → 1024 px) present in `AppIcon.appiconset`.

## What's not yet built
- Library screen (skills, MCP connections, agent definitions)
- Workspaces screen (create / manage workspaces)
- Session detail screen (live SSE feed, event timeline)
- Settings screen (API key rotation, theme toggle, app preferences)
- MCP setup onboarding step
- Custom model ID entry (TODO in `api_key_screen.dart`)
- Dark-mode theme switch (wired in `LoomTheme` but `themeMode` is hardcoded to `ThemeMode.light`)
- "View logs" action on the engine-down overlay

## Running locally

```sh
# Build the backend JAR first (from repo root)
./gradlew :backend:loom:bootJar

# Run the Flutter app (macOS)
cd frontend
flutter run -d macos
```

The router's onboarding guard is temporarily bypassed (`onboarded = true`) in
`router.dart` to land directly on the dashboard during development.

## Dependencies
- `flutter_riverpod` — state management
- `go_router` — declarative routing
- `dio` — HTTP client
- `flutter_secure_storage` — OS keychain
- `shared_preferences` — non-sensitive flags
