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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.LoomEnv;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowDefinition;
import com.loom.engine.agent.AgentRuntime;
import com.loom.engine.executor.ChainExecutor;
import com.loom.engine.executor.SupervisorExecutor;
import com.loom.engine.graph.ExecutionPlan;
import com.loom.engine.graph.GraphResolver;
import com.loom.engine.graph.InvalidWorkflowException;
import com.loom.engine.graph.SupervisorExecutionPlan;
import com.loom.engine.node.NodeRunner;
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

    private final GraphResolver graphResolver;
    private final SessionRepository sessionRepository;
    private final WorkflowRepository workflowRepository;
    private final SSEManager sseManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Prevents duplicate concurrent runs for the same session. */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /** Thread pool for async session execution. */
    private final ExecutorService executor;

    private final ChainExecutor chainExecutor;
    private final SupervisorExecutor supervisorExecutor;

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
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
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
        ExecutorService nodeExecutor =
                new ThreadPoolExecutor(
                        4,
                        20,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(200),
                        nodeFactory,
                        new ThreadPoolExecutor.AbortPolicy());

        int workerThreads = LoomEnv.LOOM_WORKER_THREADS.getInt();
        ExecutorService workerExecutor =
                new ThreadPoolExecutor(
                        workerThreads,
                        workerThreads,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(200),
                        nodeFactory,
                        new ThreadPoolExecutor.AbortPolicy());

        NodeRunner nodeRunner =
                new NodeRunner(agentRuntime, stateManager, nodeExecutor, sseManager);
        this.chainExecutor =
                new ChainExecutor(stateManager, nodeRunner, sessionRepository, sseManager);
        this.supervisorExecutor =
                new SupervisorExecutor(
                        stateManager,
                        nodeRunner,
                        workerExecutor,
                        knowledgeRepository,
                        sessionRepository,
                        sseManager);
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
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.sseManager = sseManager;
        this.executor = executor;

        NodeRunner nodeRunner =
                new NodeRunner(agentRuntime, stateManager, nodeExecutor, sseManager);
        this.chainExecutor =
                new ChainExecutor(stateManager, nodeRunner, sessionRepository, sseManager);
        this.supervisorExecutor =
                new SupervisorExecutor(
                        stateManager, nodeRunner, executor, null, sessionRepository, sseManager);
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
        this.graphResolver = graphResolver;
        this.sessionRepository = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.sseManager = sseManager;
        this.executor = executor;

        NodeRunner nodeRunner =
                new NodeRunner(agentRuntime, stateManager, nodeExecutor, sseManager);
        this.chainExecutor =
                new ChainExecutor(stateManager, nodeRunner, sessionRepository, sseManager);
        this.supervisorExecutor =
                new SupervisorExecutor(
                        stateManager,
                        nodeRunner,
                        workerExecutor,
                        knowledgeRepository,
                        sessionRepository,
                        sseManager);
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
        long timeoutSeconds = nodeTimeoutSeconds();
        if (plan instanceof SupervisorExecutionPlan sup) {
            supervisorExecutor.run(session, sup, inputContext, timeoutSeconds);
        } else {
            chainExecutor.run(session, plan, inputContext, timeoutSeconds);
        }
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
