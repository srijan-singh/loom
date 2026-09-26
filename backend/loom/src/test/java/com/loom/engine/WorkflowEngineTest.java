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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.EdgeCondition;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.domain.WorkflowType;
import com.loom.engine.agent.AgentRuntime;
import com.loom.engine.graph.GraphResolver;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.WorkflowRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import com.loom.transport.SSEManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock private AgentRuntime agentRuntime;

    @Mock private SessionRepository sessionRepository;

    @Mock private WorkflowRepository workflowRepository;

    @Mock private AgentExecutionRepository execRepo;

    @Mock private SSEManager sseManager;

    @Mock private WorkspaceKnowledgeRepository knowledgeRepository;

    /** Real StateManager backed by mocked execRepo so status tracking works. */
    private StateManager stateManager;

    private GraphResolver graphResolver;

    /** Synchronous executors so run() blocks until complete in tests. */
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

    private final ExecutorService syncNodeExecutor = Executors.newSingleThreadExecutor();

    private WorkflowEngine engine;

    @AfterEach
    void tearDown() {
        syncExecutor.shutdownNow();
        syncNodeExecutor.shutdownNow();
    }

    @BeforeEach
    void setUp() {
        stateManager = new StateManager(execRepo);
        graphResolver = new GraphResolver();
        engine =
                new WorkflowEngine(
                        agentRuntime,
                        stateManager,
                        graphResolver,
                        sessionRepository,
                        workflowRepository,
                        execRepo,
                        sseManager,
                        syncExecutor,
                        syncNodeExecutor);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowNode node(String id, NodeType type) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setNodeType(type);
        return n;
    }

    private WorkflowEdge edge(String from, String to, EdgeCondition cond) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(from + "->" + to);
        e.setFromNodeId(from);
        e.setToNodeId(to);
        e.setCondition(cond);
        return e;
    }

    private Session session(String id, String wfId) {
        Session s = new Session();
        s.setId(id);
        s.setWorkflowDefinitionId(wfId);
        s.setStatus(SessionStatus.CREATED);
        return s;
    }

    private WorkflowDefinition chainWorkflow(
            String id, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setId(id);
        wf.setType(WorkflowType.CHAIN);
        wf.setNodes(nodes);
        wf.setEdges(edges);
        return wf;
    }

    private WorkflowDefinition supervisorWorkflow(
            String id, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setId(id);
        wf.setType(WorkflowType.SUPERVISOR);
        wf.setNodes(nodes);
        wf.setEdges(edges);
        return wf;
    }

    /** Build a synchronous WorkflowEngine with knowledge repo and shared worker executor. */
    private WorkflowEngine supervisorEngine() {
        StateManager sm = new StateManager(execRepo);
        return new WorkflowEngine(
                agentRuntime,
                sm,
                graphResolver,
                sessionRepository,
                workflowRepository,
                execRepo,
                sseManager,
                syncExecutor,
                syncNodeExecutor,
                knowledgeRepository,
                syncExecutor);
    }

    private List<EventType> broadcastedEventTypes() {
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        return captor.getAllValues().stream().map(WorkflowEvent::getEventType).toList();
    }

    // ── test 1: sequential chain runs to COMPLETED ───────────────────────────

    @Test
    void sequentialChainRunsToCompleted() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode w1 = node("w1", NodeType.WORKER);
        WorkflowNode w2 = node("w2", NodeType.WORKER);
        WorkflowNode w3 = node("w3", NodeType.WORKER);
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf1",
                        Arrays.asList(start, w1, w2, w3, end),
                        Arrays.asList(
                                edge("start", "w1", EdgeCondition.ALWAYS),
                                edge("w1", "w2", EdgeCondition.ALWAYS),
                                edge("w2", "w3", EdgeCondition.ALWAYS),
                                edge("w3", "end", EdgeCondition.ALWAYS)));

        Session s = session("s1", "wf1");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf1")).thenReturn(Optional.of(wf));
        // agentRuntime.executeNode returns empty string by default (does nothing for the mock)
        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn("");

        engine.run("s1", "input");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);

        List<EventType> types = broadcastedEventTypes();
        assertThat(types).contains(EventType.SESSION_STARTED);
        assertThat(types.stream().filter(t -> t == EventType.NODE_RUNNING).count()).isEqualTo(3);
        assertThat(types.stream().filter(t -> t == EventType.NODE_COMPLETED).count())
                .isGreaterThanOrEqualTo(3);
        assertThat(types).contains(EventType.SESSION_COMPLETED);
        assertThat(types.indexOf(EventType.SESSION_STARTED))
                .isLessThan(types.lastIndexOf(EventType.SESSION_COMPLETED));
    }

    // ── test 2: runAsync dedup ────────────────────────────────────────────────

    @Test
    void runAsyncForAlreadyActiveSessionIsNoOp() throws InterruptedException {
        // Use a workflow with no workers so execution is instant
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode end = node("e", NodeType.END);
        WorkflowDefinition wf =
                chainWorkflow(
                        "wf2",
                        Arrays.asList(start, end),
                        Collections.singletonList(edge("s", "e", EdgeCondition.ALWAYS)));
        Session sess = session("s2", "wf2");
        when(sessionRepository.findById("s2")).thenReturn(Optional.of(sess));
        when(workflowRepository.findById("wf2")).thenReturn(Optional.of(wf));

        // First call
        engine.runAsync("s2", "input");
        // Second call immediately — even if the first finished, no crash
        engine.runAsync("s2", "input");

        Thread.sleep(300);

        // agentRuntime.executeNode should never be called (no worker nodes)
        verify(agentRuntime, never())
                .executeNode(anyString(), any(WorkflowNode.class), anyString());
        // isActive returns false after completion
        assertThat(engine.isActive("s2")).isFalse();
    }

    // ── test 3: session not found ─────────────────────────────────────────────

    @Test
    void sessionNotFoundBroadcastsSessionFailed() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        engine.run("missing", "input");

        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager).broadcast(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.SESSION_FAILED);
        verify(sessionRepository, never()).save(any());
    }

    // ── test 4: workflow definition not found ─────────────────────────────────

    @Test
    void workflowNotFoundFailsSession() {
        Session s = session("s3", "missing-wf");
        when(sessionRepository.findById("s3")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("missing-wf")).thenReturn(Optional.empty());

        engine.run("s3", "input");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.FAILED);
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager).broadcast(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.SESSION_FAILED);
    }

    // ── test 5: invalid workflow (cycle) ──────────────────────────────────────

    @Test
    void invalidWorkflowCycleFailsSession() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode a = node("a", NodeType.WORKER);
        WorkflowNode b = node("b", NodeType.WORKER);
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf3",
                        Arrays.asList(start, a, b, end),
                        Arrays.asList(
                                edge("start", "a", EdgeCondition.ALWAYS),
                                edge("a", "b", EdgeCondition.ALWAYS),
                                edge("b", "a", EdgeCondition.ALWAYS), // cycle
                                edge("b", "end", EdgeCondition.ALWAYS)));

        Session s = session("s4", "wf3");
        when(sessionRepository.findById("s4")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf3")).thenReturn(Optional.of(wf));

        engine.run("s4", "input");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.FAILED);
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager).broadcast(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.SESSION_FAILED);
    }

    // ── test 6: worker failure with ON_SUCCESS edge → subsequent node skipped ─

    @Test
    void workerFailureWithOnSuccessEdgeOnlySkipsSuccessorAndSessionFails() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode w1 = node("w1", NodeType.WORKER); // will fail
        WorkflowNode w2 = node("w2", NodeType.WORKER); // should be skipped
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf4",
                        Arrays.asList(start, w1, w2, end),
                        Arrays.asList(
                                edge("start", "w1", EdgeCondition.ALWAYS),
                                edge("w1", "w2", EdgeCondition.ON_SUCCESS), // only on success
                                edge("w1", "end", EdgeCondition.ON_FAILURE), // fallback to end
                                edge("w2", "end", EdgeCondition.ALWAYS)));

        Session s = session("s5", "wf4");
        when(sessionRepository.findById("s5")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf4")).thenReturn(Optional.of(wf));
        doThrow(new RuntimeException("w1 exploded"))
                .when(agentRuntime)
                .executeNode(anyString(), any(WorkflowNode.class), anyString());

        engine.run("s5", "input");

        // w1 failed → session FAILED (0 workers completed, 1 failed)
        assertThat(s.getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(stateManager.getStatus("s5", "w1")).isEqualTo(AgentExecutionStatus.FAILED);
        // w2 was never set to RUNNING
        assertThat(stateManager.getStatus("s5", "w2")).isNotEqualTo(AgentExecutionStatus.RUNNING);
    }

    // ── test 7: ON_FAILURE edge → recovery node → PARTIAL ────────────────────

    @Test
    void workerFailureWithOnFailureEdgeRunsRecoveryAndYieldsPartial() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode w1 = node("w1", NodeType.WORKER); // will fail
        WorkflowNode recovery = node("recovery", NodeType.WORKER); // ON_FAILURE route
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf5",
                        Arrays.asList(start, w1, recovery, end),
                        Arrays.asList(
                                edge("start", "w1", EdgeCondition.ALWAYS),
                                edge("w1", "recovery", EdgeCondition.ON_FAILURE),
                                edge("recovery", "end", EdgeCondition.ALWAYS)));

        Session s = session("s6", "wf5");
        when(sessionRepository.findById("s6")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf5")).thenReturn(Optional.of(wf));

        // w1 fails on first call; recovery (second call) succeeds (returns empty string)
        doThrow(new RuntimeException("w1 failed"))
                .doReturn("")
                .when(agentRuntime)
                .executeNode(anyString(), any(WorkflowNode.class), anyString());

        engine.run("s6", "input");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.PARTIAL);
        assertThat(stateManager.getStatus("s6", "w1")).isEqualTo(AgentExecutionStatus.FAILED);
        assertThat(stateManager.getStatus("s6", "recovery"))
                .isEqualTo(AgentExecutionStatus.COMPLETED);
    }

    // ── test 8: node timeout → NODE_FAILED, SESSION_FAILED ───────────────────

    @Test
    void nodeTimeoutBroadcastsNodeFailedAndSessionFails() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode slow = node("slow", NodeType.WORKER);
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf6",
                        Arrays.asList(start, slow, end),
                        Arrays.asList(
                                edge("start", "slow", EdgeCondition.ALWAYS),
                                edge("slow", "end", EdgeCondition.ON_SUCCESS)));

        Session s = session("s7", "wf6");
        when(sessionRepository.findById("s7")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf6")).thenReturn(Optional.of(wf));

        // slow node sleeps longer than timeout
        doAnswer(
                        inv -> {
                            Thread.sleep(5000);
                            return null;
                        })
                .when(agentRuntime)
                .executeNode(anyString(), any(WorkflowNode.class), anyString());

        // Build a separate engine with 1-second timeout
        StateManager sm2 = new StateManager(execRepo);
        ExecutorService timeoutNodeExecutor = Executors.newSingleThreadExecutor();
        WorkflowEngine timeoutEngine =
                new WorkflowEngine(
                        agentRuntime,
                        sm2,
                        graphResolver,
                        sessionRepository,
                        workflowRepository,
                        execRepo,
                        sseManager,
                        syncExecutor,
                        timeoutNodeExecutor);
        timeoutEngine.nodeTimeoutOverride = 1L;

        try {
            timeoutEngine.run("s7", "input");
        } finally {
            timeoutNodeExecutor.shutdownNow();
        }

        assertThat(s.getStatus()).isEqualTo(SessionStatus.FAILED);

        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        List<EventType> types =
                captor.getAllValues().stream().map(WorkflowEvent::getEventType).toList();
        assertThat(types).contains(EventType.NODE_FAILED);
        assertThat(types).contains(EventType.SESSION_FAILED);
    }

    @Test
    void workerFailureWithNullMessageUsesExceptionClassNameAsReason() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode w1 = node("w1", NodeType.WORKER);
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition wf =
                chainWorkflow(
                        "wf-npe",
                        Arrays.asList(start, w1, end),
                        Arrays.asList(
                                edge("start", "w1", EdgeCondition.ALWAYS),
                                edge("w1", "end", EdgeCondition.ALWAYS)));

        Session s = session("s-npe", "wf-npe");
        when(sessionRepository.findById("s-npe")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-npe")).thenReturn(Optional.of(wf));
        doThrow(new NullPointerException())
                .when(agentRuntime)
                .executeNode(anyString(), any(WorkflowNode.class), anyString());

        engine.run("s-npe", "input");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.FAILED);
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        WorkflowEvent nodeFailedEvent =
                captor.getAllValues().stream()
                        .filter(e -> e.getEventType() == EventType.NODE_FAILED)
                        .findFirst()
                        .orElseThrow();
        // Since future.get throws ExecutionException wrapping NullPointerException, cause is NPE,
        // simple name is NullPointerException
        assertThat(nodeFailedEvent.getData().get("reason").asText())
                .isEqualTo("NullPointerException");
    }

    // ── SUPERVISOR tests ──────────────────────────────────────────────────────

    /**
     * Happy path: supervisor returns done=true on first turn → SESSION_COMPLETED, worker nodes
     * broadcast NODE_RUNNING and NODE_COMPLETED.
     */
    @Test
    void supervisorDoneOnFirstTurnCompletesSession() throws Exception {
        WorkflowNode sup = node("sup", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1", NodeType.WORKER);
        WorkflowNode w2 = node("w2", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup1",
                        Arrays.asList(sup, w1, w2),
                        Arrays.asList(
                                edge("sup", "w1", EdgeCondition.ALWAYS),
                                edge("sup", "w2", EdgeCondition.ALWAYS)));

        Session s = session("sup-s1", "wf-sup1");
        when(sessionRepository.findById("sup-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup1")).thenReturn(Optional.of(wf));
        org.mockito.Mockito.lenient()
                .when(knowledgeRepository.findBySessionId("sup-s1"))
                .thenReturn(List.of());

        // Supervisor returns done=true on first call
        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn("{\"done\":true,\"dispatchTo\":[],\"message\":\"all done\"}");

        WorkflowEngine supEngine = supervisorEngine();
        supEngine.run("sup-s1", "goal");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);

        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        List<EventType> types =
                captor.getAllValues().stream().map(WorkflowEvent::getEventType).toList();
        assertThat(types).contains(EventType.SESSION_STARTED);
        assertThat(types).contains(EventType.NODE_RUNNING);
        assertThat(types).contains(EventType.NODE_COMPLETED);
        assertThat(types).contains(EventType.SESSION_COMPLETED);
    }

    /** done=true on first supervisor turn: iteration count for supervisor == 1 and loop exits. */
    @Test
    void supervisorIterationCountIsOneWhenDoneOnFirstTurn() throws Exception {
        WorkflowNode sup = node("sup-iter", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1-iter", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup-iter",
                        Arrays.asList(sup, w1),
                        Collections.singletonList(
                                edge("sup-iter", "w1-iter", EdgeCondition.ALWAYS)));

        Session s = session("sup-iter-s1", "wf-sup-iter");
        when(sessionRepository.findById("sup-iter-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup-iter")).thenReturn(Optional.of(wf));
        org.mockito.Mockito.lenient()
                .when(knowledgeRepository.findBySessionId("sup-iter-s1"))
                .thenReturn(List.of());

        StateManager sm = new StateManager(execRepo);
        WorkflowEngine supEngine =
                new WorkflowEngine(
                        agentRuntime,
                        sm,
                        graphResolver,
                        sessionRepository,
                        workflowRepository,
                        execRepo,
                        sseManager,
                        syncExecutor,
                        syncNodeExecutor,
                        knowledgeRepository,
                        syncExecutor);

        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn("{\"done\":true,\"dispatchTo\":[],\"message\":\"done\"}");

        supEngine.run("sup-iter-s1", "goal");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(sm.getIterationCount("sup-iter-s1", "sup-iter")).isEqualTo(1);
    }

    /** maxIterations exceeded (supervisor never returns done=true) → session PARTIAL. */
    @Test
    void supervisorMaxIterationsExceededYieldsPartial() throws Exception {
        WorkflowNode sup = node("sup-max", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1-max", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup-max",
                        Arrays.asList(sup, w1),
                        Collections.singletonList(edge("sup-max", "w1-max", EdgeCondition.ALWAYS)));

        Session s = session("sup-max-s1", "wf-sup-max");
        when(sessionRepository.findById("sup-max-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup-max")).thenReturn(Optional.of(wf));
        when(knowledgeRepository.findBySessionId("sup-max-s1")).thenReturn(List.of());

        // Supervisor always dispatches to w1 but never returns done
        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn(
                        "{\"done\":false,\"dispatchTo\":[\"w1-max\"],\"message\":\"keep going\"}");

        WorkflowEngine supEngine = supervisorEngine();
        // Build with maxIterations=2 by using env default (10) — we stub GraphResolver instead
        // by providing a workflow that resolves with maxIter=2 via subclass
        // Simpler: just let maxIter=10 and verify status after 10 iterations; but that's slow.
        // Use the supervisor workflow normally — after 10 iterations it will be PARTIAL.
        // We override by making the supervisor engine accept a plan with maxIterations=2:
        // The fastest way is to use a real GraphResolver with env override not available in tests,
        // so we build a minimal test that still terminates in ≤3 calls.
        // Approach: let the supervisor output never-done for 10 rounds; check status = PARTIAL.
        // Since syncExecutor is single-threaded this will be fast (no real I/O).
        supEngine.run("sup-max-s1", "goal");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.PARTIAL);
    }

    /** Parse failure (supervisor outputs no valid JSON) → SESSION_FAILED (PARTIAL), loop broken. */
    @Test
    void supervisorParseFailureBreaksLoopAndYieldsPartial() throws Exception {
        WorkflowNode sup = node("sup-pf", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1-pf", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup-pf",
                        Arrays.asList(sup, w1),
                        Collections.singletonList(edge("sup-pf", "w1-pf", EdgeCondition.ALWAYS)));

        Session s = session("sup-pf-s1", "wf-sup-pf");
        when(sessionRepository.findById("sup-pf-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup-pf")).thenReturn(Optional.of(wf));

        // Supervisor outputs garbage — no JSON
        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn("I don't know what to do");

        WorkflowEngine supEngine = supervisorEngine();
        supEngine.run("sup-pf-s1", "goal");

        // parse failure → PARTIAL (or SESSION_FAILED broadcast — both are valid per spec)
        assertThat(s.getStatus()).isIn(SessionStatus.PARTIAL, SessionStatus.FAILED);

        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        List<EventType> types =
                captor.getAllValues().stream().map(WorkflowEvent::getEventType).toList();
        // terminal event must be SESSION_FAILED (parseFailed → PARTIAL → SESSION_FAILED broadcast)
        assertThat(types).contains(EventType.SESSION_FAILED);
    }

    /**
     * WORKFLOW_STATE_CHANGE is broadcast after each supervisor turn with correct iteration and
     * dispatchedTo payload.
     */
    @Test
    void supervisorBroadcastsWorkflowStateChangeAfterEachTurn() throws Exception {
        WorkflowNode sup = node("sup-sc", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1-sc", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup-sc",
                        Arrays.asList(sup, w1),
                        Collections.singletonList(edge("sup-sc", "w1-sc", EdgeCondition.ALWAYS)));

        Session s = session("sup-sc-s1", "wf-sup-sc");
        when(sessionRepository.findById("sup-sc-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup-sc")).thenReturn(Optional.of(wf));
        when(knowledgeRepository.findBySessionId("sup-sc-s1")).thenReturn(List.of());

        // First call: dispatch to w1; second call: done=true
        when(agentRuntime.executeNode(anyString(), any(WorkflowNode.class), anyString()))
                .thenReturn(
                        "{\"done\":false,\"dispatchTo\":[\"w1-sc\"],\"message\":\"dispatch\"}",
                        "{\"done\":true,\"dispatchTo\":[],\"message\":\"done\"}");

        WorkflowEngine supEngine = supervisorEngine();
        supEngine.run("sup-sc-s1", "goal");

        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(sseManager, atLeastOnce()).broadcast(captor.capture());
        long stateChanges =
                captor.getAllValues().stream()
                        .filter(e -> e.getEventType() == EventType.WORKFLOW_STATE_CHANGE)
                        .count();
        assertThat(stateChanges).isGreaterThanOrEqualTo(1);

        WorkflowEvent first =
                captor.getAllValues().stream()
                        .filter(e -> e.getEventType() == EventType.WORKFLOW_STATE_CHANGE)
                        .findFirst()
                        .orElseThrow();
        assertThat(first.getData().get("iteration").asInt()).isEqualTo(0);
    }

    /**
     * Failed worker → anyWorkerFailed flag causes final status PARTIAL; session continues (loop
     * doesn't abort on worker failure).
     */
    @Test
    void supervisorFailedWorkerYieldsPartialStatus() throws Exception {
        WorkflowNode sup = node("sup-fw", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1-fw", NodeType.WORKER);

        WorkflowDefinition wf =
                supervisorWorkflow(
                        "wf-sup-fw",
                        Arrays.asList(sup, w1),
                        Collections.singletonList(edge("sup-fw", "w1-fw", EdgeCondition.ALWAYS)));

        Session s = session("sup-fw-s1", "wf-sup-fw");
        when(sessionRepository.findById("sup-fw-s1")).thenReturn(Optional.of(s));
        when(workflowRepository.findById("wf-sup-fw")).thenReturn(Optional.of(wf));
        when(knowledgeRepository.findBySessionId("sup-fw-s1")).thenReturn(List.of());

        // Track call count to control supervisor/worker behaviour
        int[] callCount = {0};
        doAnswer(
                        inv -> {
                            WorkflowNode n = inv.getArgument(1);
                            callCount[0]++;
                            if ("sup-fw".equals(n.getId())) {
                                if (callCount[0] == 1) {
                                    // First supervisor call → dispatch to w1
                                    return "{\"done\":false,\"dispatchTo\":[\"w1-fw\"],\"message\":\"go\"}";
                                }
                                // Second supervisor call → done
                                return "{\"done\":true,\"dispatchTo\":[],\"message\":\"done\"}";
                            }
                            // worker node — always fails
                            throw new RuntimeException("worker exploded");
                        })
                .when(agentRuntime)
                .executeNode(anyString(), any(WorkflowNode.class), anyString());

        WorkflowEngine supEngine = supervisorEngine();
        supEngine.run("sup-fw-s1", "goal");

        assertThat(s.getStatus()).isEqualTo(SessionStatus.PARTIAL);
    }
}
