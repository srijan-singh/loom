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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.loom.domain.NodeType;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import org.junit.jupiter.api.Test;

class SupervisorExecutionPlanTest {

    private WorkflowNode node(String id, NodeType type) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setNodeType(type);
        return n;
    }

    private SupervisorExecutionPlan buildPlan(
            WorkflowNode supervisor, List<WorkflowNode> workers, int maxIterations) {
        WorkflowNode w1 = workers.get(0);
        WorkflowNode w2 = workers.size() > 1 ? workers.get(1) : null;

        List<WorkflowNode> orderedNodes =
                w2 != null ? Arrays.asList(supervisor, w1, w2) : Arrays.asList(supervisor, w1);

        Map<String, WorkflowNode> nodeById =
                w2 != null
                        ? Map.of(supervisor.getId(), supervisor, w1.getId(), w1, w2.getId(), w2)
                        : Map.of(supervisor.getId(), supervisor, w1.getId(), w1);

        Map<String, List<WorkflowEdge>> edgesByFromNodeId =
                w2 != null
                        ? Map.of(
                                supervisor.getId(), Collections.emptyList(),
                                w1.getId(), Collections.emptyList(),
                                w2.getId(), Collections.emptyList())
                        : Map.of(
                                supervisor.getId(), Collections.emptyList(),
                                w1.getId(), Collections.emptyList());

        Map<String, List<WorkflowNode>> dispatchEdges = Map.of(supervisor.getId(), workers);

        Map<String, WorkflowNode> reportBackEdges;
        if (w2 != null) {
            reportBackEdges = Map.of(w1.getId(), supervisor, w2.getId(), supervisor);
        } else {
            reportBackEdges = Map.of(w1.getId(), supervisor);
        }

        return new SupervisorExecutionPlan(
                orderedNodes,
                nodeById,
                edgesByFromNodeId,
                supervisor,
                workers,
                dispatchEdges,
                reportBackEdges,
                maxIterations);
    }

    @Test
    void allGettersReturnCorrectValues() {
        WorkflowNode supervisor = node("sup", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1", NodeType.WORKER);
        WorkflowNode w2 = node("w2", NodeType.WORKER);
        List<WorkflowNode> workers = Arrays.asList(w1, w2);

        SupervisorExecutionPlan plan = buildPlan(supervisor, workers, 5);

        assertThat(plan.getSupervisorNode()).isEqualTo(supervisor);
        assertThat(plan.getWorkerNodes()).containsExactly(w1, w2);
        assertThat(plan.getDispatchEdges()).containsKey("sup");
        assertThat(plan.getDispatchEdges().get("sup")).containsExactly(w1, w2);
        assertThat(plan.getReportBackEdges()).containsEntry("w1", supervisor);
        assertThat(plan.getReportBackEdges()).containsEntry("w2", supervisor);
        assertThat(plan.getMaxIterations()).isEqualTo(5);
    }

    @Test
    void inheritedGetOrderedNodesReturnsPassedList() {
        WorkflowNode supervisor = node("sup", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1", NodeType.WORKER);
        List<WorkflowNode> workers = Collections.singletonList(w1);

        SupervisorExecutionPlan plan = buildPlan(supervisor, workers, 5);

        assertThat(plan.getOrderedNodes()).containsExactly(supervisor, w1);
    }

    @Test
    void maxIterationsTenIsAccessible() {
        WorkflowNode supervisor = node("sup", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1", NodeType.WORKER);

        SupervisorExecutionPlan plan = buildPlan(supervisor, Collections.singletonList(w1), 10);

        assertThat(plan.getMaxIterations()).isEqualTo(10);
    }

    @Test
    void workerNodesListIsUnmodifiable() {
        WorkflowNode supervisor = node("sup", NodeType.SUPERVISOR);
        WorkflowNode w1 = node("w1", NodeType.WORKER);

        SupervisorExecutionPlan plan = buildPlan(supervisor, Collections.singletonList(w1), 5);

        assertThatThrownBy(() -> plan.getWorkerNodes().add(node("x", NodeType.WORKER)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
