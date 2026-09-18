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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

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
                        new SynchronousQueue<>(),
                        wfFactory,
                        new ThreadPoolExecutor.CallerRunsPolicy());

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
                        new SynchronousQueue<>(),
                        nodeFactory,
                        new ThreadPoolExecutor.CallerRunsPolicy());
    }

    // ── constructor for testing: accepts pre-built executors ─────────────────

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
     * background thread via runAsync.
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

        // 8. Execute nodes in topological order
        for (WorkflowNode node : plan.getOrderedNodes()) {
            NodeType type = node.getNodeType();

            if (type == NodeType.START) {
                stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", node.getId()));
                propagateWaiting(sessionId, node.getId(), plan, AgentExecutionStatus.COMPLETED);
                continue;
            }

            if (type == NodeType.END) {
                stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", node.getId()));
                continue;
            }

            // WORKER / SUPERVISOR node
            broadcast(sessionId, EventType.NODE_RUNNING, Map.of("nodeId", node.getId()));
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.RUNNING);

            String ctx = nodeContextMap.getOrDefault(node.getId(), inputContext);
            final String nodeId = node.getId();

            Callable<String> nodeTask =
                    () -> {
                        agentRuntime.execute(sessionId, ctx);
                        // AgentRuntime.execute does not return the output directly.
                        // Use the execution record output stored by AgentRuntime.
                        return ctx; // output propagated below via execRepo if needed
                    };

            Future<String> future = nodeExecutor.submit(nodeTask);
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                // carry context forward
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                for (WorkflowNode next : nextNodes) {
                    nodeContextMap.put(next.getId(), ctx);
                }
            } catch (TimeoutException te) {
                future.cancel(true);
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", nodeId, "reason", "timeout"));
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.FAILED);
            } catch (Exception e) {
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of(
                                "nodeId",
                                nodeId,
                                "reason",
                                e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
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

    /** Submits run(...) to the internal thread pool if the session is not already active. */
    public void runAsync(String sessionId, String inputContext) {
        if (!activeSessions.add(sessionId)) {
            log.info(
                    "WorkflowEngine: session {} is already active, ignoring duplicate runAsync",
                    sessionId);
            return;
        }
        executor.submit(
                () -> {
                    try {
                        run(sessionId, inputContext);
                    } finally {
                        activeSessions.remove(sessionId);
                    }
                });
    }

    /** Returns true if the session is currently executing. */
    public boolean isActive(String sessionId) {
        return activeSessions.contains(sessionId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void propagateWaiting(
            String sessionId, String fromNodeId, ExecutionPlan plan, AgentExecutionStatus status) {
        List<WorkflowNode> nextNodes =
                stateManager.resolveNextNodes(sessionId, fromNodeId, plan, status);
        for (WorkflowNode next : nextNodes) {
            stateManager.setStatus(sessionId, next.getId(), AgentExecutionStatus.PENDING);
            broadcast(sessionId, EventType.NODE_WAITING, Map.of("nodeId", next.getId()));
        }
    }

    private void failSession(Session session, String reason) {
        session.setStatus(SessionStatus.FAILED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);
        broadcast(session.getId(), EventType.SESSION_FAILED, Map.of("error", reason));
    }

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
