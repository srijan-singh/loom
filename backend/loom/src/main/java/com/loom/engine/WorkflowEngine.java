/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.loom.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowNode;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.WorkflowRepository;
import com.loom.transport.SSEManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the execution of a workflow session by resolving the {@link ExecutionPlan}, managing
 * per-node status transitions via {@link StateManager}, running each activated node on a bounded
 * thread pool with a configurable timeout, and broadcasting lifecycle SSE events.
 *
 * <p>Activation is condition-aware: only nodes reachable through a matched {@link
 * com.loom.domain.EdgeCondition} (ON_SUCCESS, ON_FAILURE, or ALWAYS) from the preceding node are
 * executed; all others are silently skipped.
 */
@Slf4j
public class WorkflowEngine {

    private static final long DEFAULT_NODE_TIMEOUT_SECONDS = 120L;

    /** Overridable in tests by setting directly. */
    long nodeTimeoutOverride = -1L;

    private final AgentRuntime agentRuntime;
    private final StateManager stateManager;
    private final GraphResolver graphResolver;
    private final SessionRepository sessionRepository;
    private final WorkflowRepository workflowRepository;
    private final AgentExecutionRepository executionRepository;
    private final SSEManager sseManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Prevents duplicate concurrent runs for the same session. */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /** Thread pool for async session execution. */
    private final ExecutorService executor;

    /** Separate single-thread pool used to run individual nodes with timeout. */
    private final ExecutorService nodeExecutor;

    /**
     * Constructs a WorkflowEngine with production-ready bounded thread pools.
     *
     * <p>Creates a workflow executor (core 4, max 20, queue 100) for session-level concurrency and
     * a node executor (core 4, max 20, queue 200) for per-node timeout isolation. Both use {@link
     * java.util.concurrent.ThreadPoolExecutor.AbortPolicy} so rejected submissions surface as
     * {@link java.util.concurrent.RejectedExecutionException} rather than running on the caller's
     * thread.
     *
     * @param agentRuntime runtime used to execute individual workflow nodes
     * @param stateManager tracker for per-node execution status
     * @param graphResolver validator and topological sorter for workflow definitions
     * @param sessionRepository repository for reading and updating session records
     * @param workflowRepository repository for loading workflow definitions
     * @param executionRepository repository for persisting agent execution records
     * @param sseManager manager used to broadcast SSE events
     */
    public WorkflowEngine(
            AgentRuntime agentRuntime,
            StateManager stateManager,
            GraphResolver graphResolver,
            SessionRepository sessionRepository,
            WorkflowRepository workflowRepository,
            AgentExecutionRepository executionRepository,
            SSEManager sseManager) {
        this.agentRuntime = agentRuntime;
        this.stateManager = stateManager;
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.sseManager = sseManager;

        ThreadFactory wfFactory =
                r -> {
                    Thread t = new Thread(r, "wf-runner");
                    t.setDaemon(true);
                    return t;
                };
        this.executor =
                new ThreadPoolExecutor(
                        4,
                        20,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(100),
                        wfFactory,
                        new ThreadPoolExecutor.AbortPolicy());

        ThreadFactory nodeFactory =
                r -> {
                    Thread t = new Thread(r, "wf-node-runner");
                    t.setDaemon(true);
                    return t;
                };
        this.nodeExecutor =
                new ThreadPoolExecutor(
                        4,
                        20,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(200),
                        nodeFactory,
                        new ThreadPoolExecutor.AbortPolicy());
    }

    // ── constructor for testing: accepts pre-built executors ─────────────────

    /**
     * Package-private constructor for tests. Accepts externally supplied executors so tests can
     * inject synchronous or deterministic thread pools without starting background threads.
     *
     * @param agentRuntime runtime used to execute individual workflow nodes
     * @param stateManager tracker for per-node execution status
     * @param graphResolver validator and topological sorter for workflow definitions
     * @param sessionRepository repository for reading and updating session records
     * @param workflowRepository repository for loading workflow definitions
     * @param executionRepository repository for persisting agent execution records
     * @param sseManager manager used to broadcast SSE events
     * @param executor executor used to submit full-session runs
     * @param nodeExecutor executor used to run individual node tasks with timeout
     */
    WorkflowEngine(
            AgentRuntime agentRuntime,
            StateManager stateManager,
            GraphResolver graphResolver,
            SessionRepository sessionRepository,
            WorkflowRepository workflowRepository,
            AgentExecutionRepository executionRepository,
            SSEManager sseManager,
            ExecutorService executor,
            ExecutorService nodeExecutor) {
        this.agentRuntime = agentRuntime;
        this.stateManager = stateManager;
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.sseManager = sseManager;
        this.executor = executor;
        this.nodeExecutor = nodeExecutor;
    }

    /**
     * Synchronous execution of the workflow for the given session. Intended to be called from a
     * background thread via {@link #runAsync}.
     *
     * <p>Loads the session and workflow, resolves the execution plan, then iterates nodes in
     * topological order. Only condition-activated nodes are executed. After the loop, derives the
     * terminal {@link SessionStatus} (COMPLETED / PARTIAL / FAILED) and persists it.
     *
     * @param sessionId id of the session to execute
     * @param inputContext initial prompt or context string passed to the first worker node
     */
    public void run(String sessionId, String inputContext) {
        long timeoutSeconds = nodeTimeoutSeconds();

        // 1. Load session
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("WorkflowEngine: session not found: {}", sessionId);
            broadcast(sessionId, EventType.SESSION_FAILED, Map.of("error", "session not found"));
            return;
        }
        Session session = sessionOpt.get();

        // 2. Load workflow definition
        Optional<WorkflowDefinition> wfOpt =
                workflowRepository.findById(session.getWorkflowDefinitionId());
        if (wfOpt.isEmpty()) {
            failSession(session, "workflow definition not found");
            return;
        }
        WorkflowDefinition workflow = wfOpt.get();

        // 3. Resolve execution plan
        ExecutionPlan plan;
        try {
            plan = graphResolver.resolve(workflow);
        } catch (InvalidWorkflowException e) {
            log.warn(
                    "WorkflowEngine: invalid workflow for session={}: {}",
                    sessionId,
                    e.getMessage());
            failSession(session, e.getMessage());
            return;
        }

        // 4. Set session to RUNNING
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // 5. Initialise all nodes as PENDING
        for (WorkflowNode node : plan.getOrderedNodes()) {
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.PENDING);
        }

        // 6. Broadcast SESSION_STARTED
        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        // 7. nodeContextMap: carry output→input per node
        Map<String, String> nodeContextMap = new HashMap<>();
        WorkflowNode startNode = plan.getOrderedNodes().get(0);
        nodeContextMap.put(startNode.getId(), inputContext);

        // activatedNodes: tracks which nodes are eligible to run based on edge conditions.
        // The START node is always activated.
        Set<String> activatedNodes = new HashSet<>();
        activatedNodes.add(startNode.getId());

        // 8. Execute nodes in topological order
        for (WorkflowNode node : plan.getOrderedNodes()) {
            NodeType type = node.getNodeType();
            final String nodeId = node.getId();

            if (type == NodeType.START) {
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                // Activate successors reachable via START's outgoing edges
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                for (WorkflowNode next : nextNodes) {
                    activatedNodes.add(next.getId());
                    nodeContextMap.put(next.getId(), inputContext);
                }
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                continue;
            }

            if (type == NodeType.END) {
                // Only execute END if it was activated via an eligible edge
                if (!activatedNodes.contains(nodeId)) {
                    continue;
                }
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                continue;
            }

            // WORKER / SUPERVISOR node — skip if not activated by an eligible upstream edge
            if (!activatedNodes.contains(nodeId)) {
                continue;
            }

            broadcast(sessionId, EventType.NODE_RUNNING, Map.of("nodeId", nodeId));
            stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.RUNNING);

            String ctx = nodeContextMap.getOrDefault(nodeId, inputContext);

            Callable<String> nodeTask = () -> agentRuntime.executeNode(sessionId, node, ctx);

            Future<String> future = nodeExecutor.submit(nodeTask);
            try {
                String output = future.get(timeoutSeconds, TimeUnit.SECONDS);
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                // Activate condition-matched successors and carry output as their context
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                for (WorkflowNode next : nextNodes) {
                    activatedNodes.add(next.getId());
                    nodeContextMap.put(next.getId(), output != null ? output : ctx);
                }
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
            } catch (TimeoutException te) {
                future.cancel(true);
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", nodeId, "reason", "timeout"));
                // Activate ON_FAILURE successors
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.FAILED);
                for (WorkflowNode next : nextNodes) {
                    activatedNodes.add(next.getId());
                    nodeContextMap.put(next.getId(), ctx);
                }
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.FAILED);
            } catch (Exception e) {
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.FAILED);
                Throwable target = e.getCause() != null ? e.getCause() : e;
                String reason =
                        target.getMessage() != null
                                ? target.getMessage()
                                : target.getClass().getSimpleName();
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", nodeId, "reason", reason));
                // Activate ON_FAILURE successors
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.FAILED);
                for (WorkflowNode next : nextNodes) {
                    activatedNodes.add(next.getId());
                    nodeContextMap.put(next.getId(), ctx);
                }
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.FAILED);
            }
        }

        // 9. Compute terminal status (exclude START/END from counts)
        long completedCount =
                plan.getOrderedNodes().stream()
                        .filter(
                                n ->
                                        n.getNodeType() != NodeType.START
                                                && n.getNodeType() != NodeType.END)
                        .filter(
                                n ->
                                        stateManager.getStatus(sessionId, n.getId())
                                                == AgentExecutionStatus.COMPLETED)
                        .count();
        long failedCount =
                plan.getOrderedNodes().stream()
                        .filter(
                                n ->
                                        n.getNodeType() != NodeType.START
                                                && n.getNodeType() != NodeType.END)
                        .filter(
                                n ->
                                        stateManager.getStatus(sessionId, n.getId())
                                                == AgentExecutionStatus.FAILED)
                        .count();

        SessionStatus terminalStatus;
        if (failedCount == 0) {
            terminalStatus = SessionStatus.COMPLETED;
        } else if (completedCount == 0) {
            terminalStatus = SessionStatus.FAILED;
        } else {
            terminalStatus = SessionStatus.PARTIAL;
        }

        // 10. Persist terminal session state
        session.setStatus(terminalStatus);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        // 11. Broadcast terminal event
        EventType terminalEvent =
                (terminalStatus == SessionStatus.COMPLETED)
                        ? EventType.SESSION_COMPLETED
                        : EventType.SESSION_FAILED;
        broadcast(sessionId, terminalEvent, Map.of());
    }

    /**
     * Submits run(...) to the internal thread pool if the session is not already active.
     *
     * @return {@code true} if the run was accepted, {@code false} if the session was already active
     *     or the executor rejected the task (overload).
     */
    public boolean runAsync(String sessionId, String inputContext) {
        if (!activeSessions.add(sessionId)) {
            log.info(
                    "WorkflowEngine: session {} is already active, ignoring duplicate runAsync",
                    sessionId);
            return false;
        }
        try {
            executor.submit(
                    () -> {
                        try {
                            run(sessionId, inputContext);
                        } finally {
                            activeSessions.remove(sessionId);
                        }
                    });
            return true;
        } catch (RejectedExecutionException ree) {
            activeSessions.remove(sessionId);
            log.warn("WorkflowEngine: executor overloaded, rejecting session {}", sessionId);
            return false;
        }
    }

    /** Returns true if the session is currently executing. */
    public boolean isActive(String sessionId) {
        return activeSessions.contains(sessionId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Sets all condition-matched successor nodes to PENDING and broadcasts {@link
     * com.loom.event.EventType#NODE_WAITING} for each, so clients can show them as queued.
     *
     * @param sessionId id of the active session
     * @param fromNodeId id of the node that just completed or failed
     * @param plan the current execution plan
     * @param status the terminal status of {@code fromNodeId}, used to evaluate edge conditions
     */
    private void propagateWaiting(
            String sessionId, String fromNodeId, ExecutionPlan plan, AgentExecutionStatus status) {
        List<WorkflowNode> nextNodes =
                stateManager.resolveNextNodes(sessionId, fromNodeId, plan, status);
        for (WorkflowNode next : nextNodes) {
            stateManager.setStatus(sessionId, next.getId(), AgentExecutionStatus.PENDING);
            broadcast(sessionId, EventType.NODE_WAITING, Map.of("nodeId", next.getId()));
        }
    }

    /**
     * Transitions the session to FAILED status, sets {@code completedAt}, persists it, and
     * broadcasts {@link com.loom.event.EventType#SESSION_FAILED}.
     *
     * @param session the session to fail
     * @param reason human-readable description of the failure
     */
    private void failSession(Session session, String reason) {
        session.setStatus(SessionStatus.FAILED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);
        broadcast(session.getId(), EventType.SESSION_FAILED, Map.of("error", reason));
    }

    /**
     * Broadcasts a {@link WorkflowEvent} to all connected SSE clients. Exceptions are swallowed and
     * logged so that broadcast failures never interrupt session execution.
     *
     * @param sessionId id of the session associated with the event
     * @param type the event type to broadcast
     * @param data additional key/value payload included in the event
     */
    private void broadcast(String sessionId, EventType type, Map<String, Object> data) {
        try {
            WorkflowEvent event =
                    WorkflowEvent.builder()
                            .eventType(type)
                            .sessionId(sessionId)
                            .data(mapper.valueToTree(data))
                            .build();
            sseManager.broadcast(event);
        } catch (Exception e) {
            log.error("Failed to broadcast {} for session={}", type, sessionId, e);
        }
    }

    /**
     * Returns the effective node timeout in seconds. Prefers {@link #nodeTimeoutOverride} when
     * positive, then the {@code LOOM_NODE_TIMEOUT_SECONDS} environment variable, then {@link
     * #DEFAULT_NODE_TIMEOUT_SECONDS}.
     *
     * @return timeout in seconds to apply to each node's {@link java.util.concurrent.Future#get}
     */
    long nodeTimeoutSeconds() {
        if (nodeTimeoutOverride > 0) return nodeTimeoutOverride;
        String env = System.getenv("LOOM_NODE_TIMEOUT_SECONDS");
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_NODE_TIMEOUT_SECONDS;
    }
}
