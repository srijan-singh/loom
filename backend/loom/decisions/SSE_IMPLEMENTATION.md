# ADR: SSE Implementation

**Status:** Accepted  
**Date:** 2026-07-26  
**Scope:** `com.loom.transport` — `SSEManager`, `LocalServer`, `LocalServerBVT`

---

## Context

The Loom engine needs a real-time channel for pushing `WorkflowEvent`s (node state changes, agent tokens, session lifecycle) to Flutter clients. The connection is always server-initiated: Java pushes, Flutter reads. No client-to-server messaging goes through this channel.

## Decision

Use **Server-Sent Events (SSE)** over plain HTTP, exposed via Javalin 7's built-in SSE API on `GET /events`.

### Why SSE over WebSockets

| Concern | SSE | WebSocket |
|---|---|---|
| Direction | Server → client only | Bi-directional |
| Protocol | HTTP/1.1 (works through proxies) | Upgrade handshake |
| Reconnection | Automatic (browser / Dart client) | Manual |
| Complexity | Minimal | Higher |

The event stream is unidirectional — Flutter subscribes and receives. No client-to-server messages travel through this channel, so WebSocket's bidirectionality adds complexity with no benefit.

---

## Wire Format

Javalin's `SseClient.sendEvent(String data)` writes:

```
event: message
data: <json>
\n
```

`SSEManager.broadcast()` passes the serialized `WorkflowEvent` JSON as the `data` value. Clients parse `data:` lines and deserialize the JSON payload.

---

## Implementation

### `SSEManager`

```java
private final List<SseClient> clients = new CopyOnWriteArrayList<>();
```

**`CopyOnWriteArrayList` rationale:** broadcasts (reads) happen far more frequently than connects/disconnects (writes). COWAL gives lock-free iteration at the cost of a full array copy on write — the right trade-off here.

**`attach(SseClient client)`**

```java
public void attach(SseClient client) {
    clients.add(client);
    client.onClose(() -> {
        clients.remove(client);
        log.info("Client disconnected. {} clients remaining", getClientCount());
    });
    log.info("Client connected. {} total clients", getClientCount());
    client.keepAlive();   // ← critical — see gotcha below
}
```

**`broadcast(WorkflowEvent event)`**

Serializes the event to JSON first (fail-fast before touching any client). On send failure, the client is collected into a separate `failedClients` list and removed after the iteration — mutating `CopyOnWriteArrayList` inside a `forEach` lambda is safe but wasteful; batch removal avoids unnecessary copies.

```java
List<SseClient> failedClients = new ArrayList<>();
clients.forEach(client -> {
    try {
        client.sendEvent(eventMessage);
    } catch (Exception e) {
        log.warn("Failed to send event to client: {}", e.getMessage());
        failedClients.add(client);
    }
});
if (!failedClients.isEmpty()) {
    clients.removeAll(failedClients);
}
```

### `LocalServer`

Routes are registered inside `Javalin.create(config -> { ... })` via `config.routes`, which is a `RoutesConfig` implementing `JavalinDefaultRoutingApi`. In Javalin 7, the `Javalin` class itself does **not** expose `get()` / `post()` directly — those methods live on `JavalinDefaultRoutingApi` and must be called through the config block.

```java
app = Javalin.create(config -> {
    config.routes.sse(EVENTS_ENDPOINT, sseManager::attach);
    agentRoutes.register(config.routes);
    // ...
}).start(port);
```

---

## Gotchas

### 1. `keepAlive()` is mandatory

Without calling `client.keepAlive()` in `attach()`, Javalin marks the HTTP response complete as soon as the handler method returns. The `SseClient` is immediately closed and the client connection drops. `keepAlive()` parks the request on an internal `CompletableFuture` that only resolves when `client.close()` is called.

This was the root cause of the SSE stream silently closing before any events arrived during testing.

### 2. `HttpURLConnection` cannot be used to test SSE

`HttpURLConnection` reads and buffers the full response body before making the stream available, which is incompatible with a long-lived chunked SSE response. The connection appears to succeed (HTTP 200) but data never arrives in the test thread.

**Fix:** Use OkHttp with `readTimeout(0)` (infinite). OkHttp streams the response body incrementally via `okio.BufferedSource.readUtf8Line()`, which blocks only until the next newline arrives.

### 3. `CountDownLatch` required for connection signalling

OkHttp's `Call.enqueue()` fires `onResponse` on an OkHttp dispatcher thread. Using a volatile boolean polled from the test thread introduces a race window. A `CountDownLatch(1)` is used instead: it is counted down the moment the 200 header is received inside `onResponse`, and the test thread calls `latch.await(3, SECONDS)` to block until the connection is truly established before broadcasting.

---

## Test

`LocalServerBVT` — single end-to-end test:

1. Start `LocalServer` on a random free port (`ServerSocket(0)`)
2. Connect `TestSSEClient` (OkHttp-backed, `CountDownLatch` for connection sync)
3. `SSEManager.broadcast()` a `WorkflowEvent` with known fields
4. `waitForMessage(5)` — blocks on a `BlockingQueue` populated by the OkHttp reader thread
5. Parse received JSON with Jackson and assert `eventType`, `sessionId`, `data.message`, `timestamp`

---

## Alternatives Considered

**Javalin TestTools (`javalin-testtools`)** — provides an in-process `JavalinTest.test()` helper but does not support SSE endpoints (the test transport closes the response immediately, same underlying issue as `HttpURLConnection`). A real HTTP client against a live port is required.

**`java.net.http.HttpClient` (JDK 11+)** — supports streaming via `BodyHandlers.ofInputStream()` but still subject to buffering by the JVM's HTTP implementation on some platforms. OkHttp's `okio` layer is more reliable for SSE in tests.
