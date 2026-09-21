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

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.LoomEnv;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowNode;
import com.loom.domain.WorkspaceKnowledge;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.WorkflowRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import com.loom.transport.SSEManager;

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

    /** Overridable in tests by setting directly. Package-private by design. */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    long nodeTimeoutOverride = -1L;

    private final AgentRuntime agentRuntime;
    private final StateManager stateManager;
    private final GraphResolver graphResolver;
    private final SessionRepository sessionRepository;
    private final WorkflowRepository workflowRepository;
    private final AgentExecutionRepository executionRepository;
    private final SSEManager sseManager;
    private final WorkspaceKnowledgeRepository knowledgeRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Prevents duplicate concurrent runs for the same session. */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /** Thread pool for async session execution. */
    private final ExecutorService executor;

    /** Separate single-thread pool used to run individual nodes with timeout. */
    private final ExecutorService nodeExecutor;

    /** Thread pool used to dispatch worker nodes in parallel during SUPERVISOR execution. */
    private final ExecutorService workerExecutor;

    /**
     * Constructs a WorkflowEngine with production-ready bounded thread pools.
     *
     * @param agentRuntime runtime used to execute individual workflow nodes
     * @param stateManager tracker for per-node execution status
     * @param graphResolver validator and topological sorter for workflow definitions
     * @param sessionRepository repository for reading and updating session records
     * @param workflowRepository repository for loading workflow definitions
     * @param executionRepository repository for persisting agent execution records
     * @param sseManager manager used to broadcast SSE events
     * @param knowledgeRepository repository for fetching workspace knowledge by session
     */
    public WorkflowEngine(
            AgentRuntime agentRuntime,
            StateManager stateManager,
            GraphResolver graphResolver,
            SessionRepository sessionRepository,
            WorkflowRepository workflowRepository,
            AgentExecutionRepository executionRepository,
            SSEManager sseManager,
            WorkspaceKnowledgeRepository knowledgeRepository) {
        this.agentRuntime = agentRuntime;
        this.stateManager = stateManager;
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.sseManager = sseManager;
        this.knowledgeRepository = knowledgeRepository;

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

        int workerThreads = LoomEnv.LOOM_WORKER_THREADS.getInt();
        this.workerExecutor =
                new ThreadPoolExecutor(
                        workerThreads,
                        workerThreads,
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
        this(
                agentRuntime,
                stateManager,
                graphResolver,
                sessionRepository,
                workflowRepository,
                executionRepository,
                sseManager,
                executor,
                nodeExecutor,
                null,
                executor);
    }

    /**
     * Package-private constructor for tests that also need the knowledgeRepository and
     * workerExecutor (used by SUPERVISOR tests).
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
            ExecutorService nodeExecutor,
            WorkspaceKnowledgeRepository knowledgeRepository,
            ExecutorService workerExecutor) {
        this.agentRuntime = agentRuntime;
        this.stateManager = stateManager;
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.sseManager = sseManager;
        this.executor = executor;
        this.nodeExecutor = nodeExecutor;
        this.knowledgeRepository = knowledgeRepository;
        this.workerExecutor = workerExecutor;
    }

    /**
     * Synchronous execution of the workflow for the given session. Intended to be called from a
     * background thread via {@link #runAsync}.
     *
     * @param sessionId id of the session to execute
     * @param inputContext initial prompt or context string passed to the first worker node
     */
    public void run(String sessionId, String inputContext) {
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

        // 4. Branch on plan type
        if (plan instanceof SupervisorExecutionPlan sup) {
            runSupervisor(session, sup, inputContext);
        } else {
            runChain(session, plan, inputContext);
        }
    }

    // ── CHAIN execution path ──────────────────────────────────────────────────

    private void runChain(Session session, ExecutionPlan plan, String inputContext) {
        long timeoutSeconds = nodeTimeoutSeconds();
        String sessionId = session.getId();

        // Set session to RUNNING
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // Initialise all nodes as PENDING
        for (WorkflowNode node : plan.getOrderedNodes()) {
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.PENDING);
        }

        // Broadcast SESSION_STARTED
        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        // nodeContextMap: carry output→input per node
        Map<String, String> nodeContextMap = new HashMap<>();
        WorkflowNode startNode = plan.getOrderedNodes().get(0);
        nodeContextMap.put(startNode.getId(), inputContext);

        // activatedNodes: tracks which nodes are eligible to run based on edge conditions.
        Set<String> activatedNodes = new HashSet<>();
        activatedNodes.add(startNode.getId());

        // Execute nodes in topological order
        for (WorkflowNode node : plan.getOrderedNodes()) {
            NodeType type = node.getNodeType();
            final String nodeId = node.getId();

            if (type == NodeType.START) {
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
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

        // Compute terminal status (exclude START/END from counts)
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

        session.setStatus(terminalStatus);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        EventType terminalEvent =
                (terminalStatus == SessionStatus.COMPLETED)
                        ? EventType.SESSION_COMPLETED
                        : EventType.SESSION_FAILED;
        broadcast(sessionId, terminalEvent, Map.of());
    }

    // ── SUPERVISOR execution path ─────────────────────────────────────────────

    private void runSupervisor(Session session, SupervisorExecutionPlan plan, String inputContext) {
        String sessionId = session.getId();
        WorkflowNode supervisorNode = plan.getSupervisorNode();
        long timeoutSeconds = nodeTimeoutSeconds();

        // Build the permitted worker ID set once — only these may be dispatched
        Set<String> permittedWorkerIds = new HashSet<>();
        for (WorkflowNode w : plan.getWorkerNodes()) {
            permittedWorkerIds.add(w.getId());
        }

        // Set session to RUNNING
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // Initialise all nodes as PENDING (supervisor + workers)
        for (WorkflowNode node : plan.getOrderedNodes()) {
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.PENDING);
        }

        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        List<WorkspaceKnowledge> workerReports = Collections.emptyList();
        int iteration = 0;
        boolean done = false;
        boolean parseFailed = false;
        boolean anyWorkerFailed = false;

        while (iteration < plan.getMaxIterations() && !done) {
            // --- SUPERVISOR TURN (with timeout) ---
            String supervisorCtx = buildSupervisorContext(inputContext, workerReports, iteration);
            broadcast(sessionId, EventType.NODE_RUNNING, Map.of("nodeId", supervisorNode.getId()));
            stateManager.setStatus(sessionId, supervisorNode.getId(), AgentExecutionStatus.RUNNING);

            String supervisorOutput;
            final WorkflowNode supNode = supervisorNode;
            Future<String> supFuture =
                    nodeExecutor.submit(
                            () -> agentRuntime.executeNode(sessionId, supNode, supervisorCtx));
            try {
                supervisorOutput = supFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                stateManager.setStatus(
                        sessionId, supervisorNode.getId(), AgentExecutionStatus.COMPLETED);
                broadcast(
                        sessionId,
                        EventType.NODE_COMPLETED,
                        Map.of("nodeId", supervisorNode.getId()));
            } catch (TimeoutException te) {
                supFuture.cancel(true);
                stateManager.setStatus(
                        sessionId, supervisorNode.getId(), AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", supervisorNode.getId(), "reason", "timeout"));
                parseFailed = true;
                break;
            } catch (Exception ex) {
                stateManager.setStatus(
                        sessionId, supervisorNode.getId(), AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId, EventType.NODE_FAILED, Map.of("nodeId", supervisorNode.getId()));
                parseFailed = true;
                break;
            }

            Optional<SupervisorResponse> parsedOpt = parseSupervisorResponse(supervisorOutput);
            if (parsedOpt.isEmpty()) {
                log.warn("could not parse SupervisorResponse for session {}", sessionId);
                parseFailed = true;
                break;
            }

            SupervisorResponse response = parsedOpt.get();
            if (response.isDone()) {
                done = true;
                break;
            }

            // --- WORKER DISPATCH BATCH ---
            // Deduplicate dispatch IDs (insertion-ordered, first-seen wins)
            List<String> rawDispatch =
                    response.getDispatchTo() != null ? response.getDispatchTo() : List.of();
            List<String> toDispatch = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String id : rawDispatch) {
                if (!seen.add(id)) continue; // duplicate
                WorkflowNode candidate = plan.getNode(id);
                if (candidate == null) {
                    log.warn(
                            "runSupervisor: dispatcher returned unknown nodeId '{}', skipping", id);
                    continue;
                }
                if (candidate.getNodeType() != NodeType.WORKER
                        || !permittedWorkerIds.contains(id)) {
                    log.warn(
                            "runSupervisor: nodeId '{}' is not a permitted WORKER node, skipping",
                            id);
                    continue;
                }
                toDispatch.add(id);
            }

            // Submit each worker and wait for the batch to complete.
            boolean[] workerFailedRef = {false};
            List<Future<?>> outerFutures =
                    dispatchWorkerBatch(
                            sessionId,
                            toDispatch,
                            plan,
                            inputContext,
                            timeoutSeconds,
                            workerFailedRef);
            anyWorkerFailed |=
                    joinWorkerBatch(sessionId, outerFutures, toDispatch.size(), timeoutSeconds);
            if (workerFailedRef[0]) {
                anyWorkerFailed = true;
            }

            workerReports =
                    knowledgeRepository != null
                            ? knowledgeRepository.findBySessionId(sessionId)
                            : Collections.emptyList();
            broadcast(
                    sessionId,
                    EventType.WORKFLOW_STATE_CHANGE,
                    Map.of("iteration", iteration, "dispatchedTo", toDispatch));
            iteration++;
        }

        // --- COMPUTE TERMINAL STATUS ---
        SessionStatus terminalStatus;
        if (done && !anyWorkerFailed && !parseFailed) {
            terminalStatus = SessionStatus.COMPLETED;
        } else if (parseFailed || iteration >= plan.getMaxIterations()) {
            terminalStatus = SessionStatus.PARTIAL;
        } else if (anyWorkerFailed) {
            terminalStatus = SessionStatus.PARTIAL;
        } else {
            terminalStatus = SessionStatus.COMPLETED;
        }

        session.setStatus(terminalStatus);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        EventType terminalEvent =
                (terminalStatus == SessionStatus.COMPLETED)
                        ? EventType.SESSION_COMPLETED
                        : EventType.SESSION_FAILED;
        broadcast(sessionId, terminalEvent, Map.of());
    }

    // ── supervisor helpers ────────────────────────────────────────────────────

    /**
     * Submits each worker node in {@code toDispatch} to {@link #workerExecutor}. Each worker runs
     * its own {@link AgentRuntime#executeNode} call inside {@link #nodeExecutor} with a per-node
     * timeout. On any failure the node is marked FAILED, an SSE event is broadcast, and {@code
     * workerFailedRef[0]} is set to {@code true}.
     *
     * @return the list of outer futures (one per dispatched node) for the caller to join on
     */
    @SuppressWarnings("unchecked")
    private List<Future<?>> dispatchWorkerBatch(
            String sessionId,
            List<String> toDispatch,
            SupervisorExecutionPlan plan,
            String inputContext,
            long timeoutSeconds,
            boolean[] workerFailedRef) {
        List<Future<?>> outerFutures = new ArrayList<>();
        for (String nodeId : toDispatch) {
            WorkflowNode workerNode = plan.getNode(nodeId);
            Future<String>[] workerFutureHolder = new Future[1];
            Runnable task =
                    () ->
                            runWorkerNode(
                                    sessionId,
                                    workerNode,
                                    inputContext,
                                    timeoutSeconds,
                                    workerFutureHolder,
                                    workerFailedRef);
            try {
                outerFutures.add(workerExecutor.submit(task));
            } catch (RejectedExecutionException ree) {
                log.warn(
                        "runSupervisor: workerExecutor rejected node '{}' in session {}",
                        nodeId,
                        sessionId);
                stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", workerNode.getId(), "reason", "rejected"));
                workerFailedRef[0] = true;
            }
        }
        return outerFutures;
    }

    /**
     * Executes a single worker node inside {@link #nodeExecutor} with a per-node timeout. Updates
     * node status and broadcasts SSE events on completion or failure.
     */
    private void runWorkerNode(
            String sessionId,
            WorkflowNode workerNode,
            String inputContext,
            long timeoutSeconds,
            Future<String>[] workerFutureHolder,
            boolean[] workerFailedRef) {
        broadcast(sessionId, EventType.NODE_RUNNING, Map.of("nodeId", workerNode.getId()));
        stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.RUNNING);
        Future<String> workerFuture =
                nodeExecutor.submit(
                        () -> agentRuntime.executeNode(sessionId, workerNode, inputContext));
        synchronized (workerFutureHolder) {
            workerFutureHolder[0] = workerFuture;
        }
        try {
            workerFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.COMPLETED);
            broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", workerNode.getId()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            synchronized (workerFutureHolder) {
                if (workerFutureHolder[0] != null) workerFutureHolder[0].cancel(true);
            }
            stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.FAILED);
            broadcast(
                    sessionId,
                    EventType.NODE_FAILED,
                    Map.of("nodeId", workerNode.getId(), "reason", "interrupted"));
            workerFailedRef[0] = true;
        } catch (TimeoutException te) {
            workerFuture.cancel(true);
            stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.FAILED);
            broadcast(
                    sessionId,
                    EventType.NODE_FAILED,
                    Map.of("nodeId", workerNode.getId(), "reason", "timeout"));
            workerFailedRef[0] = true;
        } catch (Exception e) {
            stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.FAILED);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String reason =
                    cause.getMessage() != null
                            ? cause.getMessage()
                            : cause.getClass().getSimpleName();
            broadcast(
                    sessionId,
                    EventType.NODE_FAILED,
                    Map.of("nodeId", workerNode.getId(), "reason", reason));
            workerFailedRef[0] = true;
        }
    }

    /**
     * Waits for all outer futures (worker-dispatch tasks) up to a bounded deadline.
     *
     * @return {@code true} if any worker timed out at the batch level
     */
    private boolean joinWorkerBatch(
            String sessionId, List<Future<?>> outerFutures, int workerCount, long timeoutSeconds) {
        long batchDeadline =
                System.currentTimeMillis() + (workerCount + 1) * timeoutSeconds * 1000L;
        try {
            for (Future<?> f : outerFutures) {
                long remaining = batchDeadline - System.currentTimeMillis();
                if (remaining > 0) {
                    f.get(remaining, TimeUnit.MILLISECONDS);
                } else {
                    f.cancel(true);
                }
            }
        } catch (TimeoutException bte) {
            log.warn("runSupervisor: worker batch timed out for session {}", sessionId);
            for (Future<?> f : outerFutures) {
                f.cancel(true);
            }
            return true;
        } catch (Exception ignored) {
            // individual worker failures are already recorded inside runWorkerNode
        }
        return false;
    }

    private Optional<SupervisorResponse> parseSupervisorResponse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return Optional.empty();
        }
        // 1. Try the whole output first (cheapest path — agent emitted only JSON)
        try {
            SupervisorResponse resp = mapper.readValue(rawOutput.trim(), SupervisorResponse.class);
            return Optional.of(resp);
        } catch (Exception ignored) {
            // not pure JSON — fall through to extraction
        }
        // 2. Use Jackson's JsonParser to scan forward for each top-level '{' and attempt a full
        //    deserialisation from that position.  Jackson handles all quoting, nesting, unicode,
        //    and escape sequences — no hand-rolled brace counting needed.  We keep the last
        //    successful parse so the semantics match "last JSON object in the output".
        SupervisorResponse last = null;
        try (JsonParser jp = mapper.createParser(rawOutput)) {
            JsonToken token;
            while ((token = jp.nextToken()) != null) {
                if (token == JsonToken.START_OBJECT) {
                    try {
                        last = mapper.readValue(jp, SupervisorResponse.class);
                    } catch (Exception ignored) {
                        // not a valid SupervisorResponse at this position — keep scanning
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed JSON stream — fall through
        }
        return last != null ? Optional.of(last) : Optional.empty();
    }

    private String buildSupervisorContext(
            String goal, List<WorkspaceKnowledge> reports, int iteration) {
        StringBuilder sb = new StringBuilder();
        sb.append("GOAL: ").append(goal).append("\n\n");
        sb.append("ITERATION: ").append(iteration).append("\n\n");
        if (reports.isEmpty()) {
            sb.append("No worker reports yet.\n");
        } else {
            sb.append("WORKER REPORTS:\n");
            for (WorkspaceKnowledge r : reports) {
                sb.append("- ")
                        .append(r.getTitle())
                        .append(": ")
                        .append(r.getContent())
                        .append("\n");
            }
        }
        return sb.toString();
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
     * positive, then {@link LoomEnv#LOOM_NODE_TIMEOUT_SECONDS}.
     */
    long nodeTimeoutSeconds() {
        if (nodeTimeoutOverride > 0) return nodeTimeoutOverride;
        return LoomEnv.LOOM_NODE_TIMEOUT_SECONDS.getLong();
    }
}
