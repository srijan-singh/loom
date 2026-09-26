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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.loom.domain.EdgeCondition;
import com.loom.domain.NodeType;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.engine.graph.ExecutionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

    private WorkflowNode startNode;
    private WorkflowNode workerNode;
    private WorkflowNode endNode;
    private WorkflowEdge edge1;
    private WorkflowEdge edge2;
    private ExecutionPlan plan;

    @BeforeEach
    void setUp() {
        startNode = new WorkflowNode();
        startNode.setId("start");
        startNode.setNodeType(NodeType.START);

        workerNode = new WorkflowNode();
        workerNode.setId("worker");
        workerNode.setNodeType(NodeType.WORKER);

        endNode = new WorkflowNode();
        endNode.setId("end");
        endNode.setNodeType(NodeType.END);

        edge1 = new WorkflowEdge();
        edge1.setId("e1");
        edge1.setFromNodeId("start");
        edge1.setToNodeId("worker");
        edge1.setCondition(EdgeCondition.ALWAYS);

        edge2 = new WorkflowEdge();
        edge2.setId("e2");
        edge2.setFromNodeId("worker");
        edge2.setToNodeId("end");
        edge2.setCondition(EdgeCondition.ON_SUCCESS);

        List<WorkflowNode> ordered = Arrays.asList(startNode, workerNode, endNode);
        Map<String, WorkflowNode> byId =
                Map.of(
                        "start", startNode,
                        "worker", workerNode,
                        "end", endNode);
        Map<String, List<WorkflowEdge>> edgeMap =
                Map.of(
                        "start", Collections.singletonList(edge1),
                        "worker", Collections.singletonList(edge2));

        plan = new ExecutionPlan(ordered, byId, edgeMap);
    }

    @Test
    void getOrderedNodesReturnsSameOrder() {
        List<WorkflowNode> nodes = plan.getOrderedNodes();
        assertThat(nodes).containsExactly(startNode, workerNode, endNode);
    }

    @Test
    void getNodeReturnsCorrectNodeForKnownId() {
        assertThat(plan.getNode("worker")).isSameAs(workerNode);
    }

    @Test
    void getNodeReturnsNullForUnknownId() {
        assertThat(plan.getNode("unknown-id")).isNull();
    }

    @Test
    void getOutgoingEdgesReturnsMatchingEdges() {
        List<WorkflowEdge> edges = plan.getOutgoingEdges("start");
        assertThat(edges).containsExactly(edge1);
    }

    @Test
    void getOutgoingEdgesReturnsEmptyListForNodeWithNoEdges() {
        List<WorkflowEdge> edges = plan.getOutgoingEdges("end");
        assertThat(edges).isNotNull().isEmpty();
    }

    @Test
    void sessionStatusPartialIsPresent() {
        assertThat(SessionStatus.values()).contains(SessionStatus.PARTIAL);
    }
}
