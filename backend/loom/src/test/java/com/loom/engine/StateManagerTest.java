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
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.loom.domain.AgentExecution;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.EdgeCondition;
import com.loom.domain.NodeType;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.engine.graph.ExecutionPlan;
import com.loom.storage.repository.AgentExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateManagerTest {

    @Mock private AgentExecutionRepository execRepo;

    private StateManager sm;

    @BeforeEach
    void setUp() {
        sm = new StateManager(execRepo);
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

    /** Minimal 2-node plan: start → end. */
    private ExecutionPlan twoNodePlan(WorkflowNode start, WorkflowNode end, WorkflowEdge e) {
        return new ExecutionPlan(
                Arrays.asList(start, end),
                Map.of(start.getId(), start, end.getId(), end),
                Map.of(
                        start.getId(), Collections.singletonList(e),
                        end.getId(), Collections.emptyList()));
    }

    // ── setStatus / getStatus ─────────────────────────────────────────────────

    @Test
    void setStatusCreatesRecordAndGetStatusReturnsIt() {
        sm.setStatus("s1", "n1", AgentExecutionStatus.RUNNING);
        assertThat(sm.getStatus("s1", "n1")).isEqualTo(AgentExecutionStatus.RUNNING);
    }

    @Test
    void setStatusCalledTwiceUpdatesNotDuplicates() {
        sm.setStatus("s2", "n1", AgentExecutionStatus.RUNNING);
        sm.setStatus("s2", "n1", AgentExecutionStatus.COMPLETED);

        assertThat(sm.getStatus("s2", "n1")).isEqualTo(AgentExecutionStatus.COMPLETED);

        // Two save calls, but both on the same in-memory object (upsert logic)
        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        // verify exactly 2 save calls were made
        verify(execRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        // the two captured executions share the same id (same object reused)
        List<AgentExecution> saved = captor.getAllValues();
        assertThat(saved.get(0).getId()).isEqualTo(saved.get(1).getId());
    }

    @Test
    void setStatusRunningPopulatesStartedAt() {
        sm.setStatus("s3", "n1", AgentExecutionStatus.RUNNING);

        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(execRepo).save(captor.capture());
        assertThat(captor.getValue().getStartedAt()).isGreaterThan(0L);
    }

    @Test
    void setStatusCompletedPopulatesCompletedAt() {
        sm.setStatus("s4", "n1", AgentExecutionStatus.RUNNING);
        sm.setStatus("s4", "n1", AgentExecutionStatus.COMPLETED);

        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(execRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        AgentExecution last = captor.getAllValues().get(1);
        assertThat(last.getCompletedAt()).isNotNull().isGreaterThan(0L);
    }

    @Test
    void getStatusForUnknownPairReturnsPending() {
        assertThat(sm.getStatus("unknown-session", "unknown-node"))
                .isEqualTo(AgentExecutionStatus.PENDING);
    }

    // ── areUpstreamsDone ──────────────────────────────────────────────────────

    @Test
    void areUpstreamsDoneReturnsTrueForStartNode() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan = twoNodePlan(start, end, edge("start", "end", EdgeCondition.ALWAYS));

        assertThat(sm.areUpstreamsDone("s5", "start", plan)).isTrue();
    }

    @Test
    void areUpstreamsDoneReturnsFalseWhenPredecessorIsRunning() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan = twoNodePlan(start, end, edge("start", "end", EdgeCondition.ALWAYS));

        sm.setStatus("s6", "start", AgentExecutionStatus.RUNNING);
        assertThat(sm.areUpstreamsDone("s6", "end", plan)).isFalse();
    }

    @Test
    void areUpstreamsDoneReturnsTrueWhenPredecessorIsCompleted() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan = twoNodePlan(start, end, edge("start", "end", EdgeCondition.ALWAYS));

        sm.setStatus("s7", "start", AgentExecutionStatus.COMPLETED);
        assertThat(sm.areUpstreamsDone("s7", "end", plan)).isTrue();
    }

    // ── resolveNextNodes ──────────────────────────────────────────────────────

    @Test
    void resolveNextNodesOnSuccessEdgeWithCompletedReturnsTarget() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan =
                twoNodePlan(start, end, edge("start", "end", EdgeCondition.ON_SUCCESS));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s8", "start", plan, AgentExecutionStatus.COMPLETED);
        assertThat(next).containsExactly(end);
    }

    @Test
    void resolveNextNodesOnSuccessEdgeWithFailedReturnsEmpty() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan =
                twoNodePlan(start, end, edge("start", "end", EdgeCondition.ON_SUCCESS));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s9", "start", plan, AgentExecutionStatus.FAILED);
        assertThat(next).isEmpty();
    }

    @Test
    void resolveNextNodesOnFailureEdgeWithFailedReturnsTarget() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan =
                twoNodePlan(start, end, edge("start", "end", EdgeCondition.ON_FAILURE));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s10", "start", plan, AgentExecutionStatus.FAILED);
        assertThat(next).containsExactly(end);
    }

    @Test
    void resolveNextNodesOnFailureEdgeWithCompletedReturnsEmpty() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan =
                twoNodePlan(start, end, edge("start", "end", EdgeCondition.ON_FAILURE));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s11", "start", plan, AgentExecutionStatus.COMPLETED);
        assertThat(next).isEmpty();
    }

    @Test
    void resolveNextNodesAlwaysEdgeWithCompletedReturnsTarget() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan = twoNodePlan(start, end, edge("start", "end", EdgeCondition.ALWAYS));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s12", "start", plan, AgentExecutionStatus.COMPLETED);
        assertThat(next).containsExactly(end);
    }

    @Test
    void resolveNextNodesAlwaysEdgeWithFailedReturnsTarget() {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode end = node("end", NodeType.END);
        ExecutionPlan plan = twoNodePlan(start, end, edge("start", "end", EdgeCondition.ALWAYS));

        List<WorkflowNode> next =
                sm.resolveNextNodes("s13", "start", plan, AgentExecutionStatus.FAILED);
        assertThat(next).containsExactly(end);
    }

    // ── getIterationCount ─────────────────────────────────────────────────────

    @Test
    void getIterationCountReturnsZeroBeforeAnyRunningCall() {
        assertThat(sm.getIterationCount("session-iter-0", "supervisor")).isEqualTo(0);
    }

    @Test
    void getIterationCountReturnsOneAfterOneRunningCall() {
        sm.setStatus("session-iter-1", "supervisor", AgentExecutionStatus.RUNNING);
        assertThat(sm.getIterationCount("session-iter-1", "supervisor")).isEqualTo(1);
    }

    @Test
    void getIterationCountReturnsThreeAfterThreeRunningCalls() {
        sm.setStatus("session-iter-3", "supervisor", AgentExecutionStatus.RUNNING);
        sm.setStatus("session-iter-3", "supervisor", AgentExecutionStatus.RUNNING);
        sm.setStatus("session-iter-3", "supervisor", AgentExecutionStatus.RUNNING);
        assertThat(sm.getIterationCount("session-iter-3", "supervisor")).isEqualTo(3);
    }

    @Test
    void getIterationCountTracksSessionsIndependently() {
        sm.setStatus("s-a", "supervisor", AgentExecutionStatus.RUNNING);
        sm.setStatus("s-a", "supervisor", AgentExecutionStatus.RUNNING);
        sm.setStatus("s-b", "supervisor", AgentExecutionStatus.RUNNING);

        assertThat(sm.getIterationCount("s-a", "supervisor")).isEqualTo(2);
        assertThat(sm.getIterationCount("s-b", "supervisor")).isEqualTo(1);
    }
}
