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

import com.loom.domain.EdgeCondition;
import com.loom.domain.NodeType;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.domain.WorkflowType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphResolverTest {

    private GraphResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GraphResolver();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowNode node(String id, NodeType type) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setNodeType(type);
        return n;
    }

    private WorkflowEdge edge(String from, String to) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(from + "->" + to);
        e.setFromNodeId(from);
        e.setToNodeId(to);
        e.setCondition(EdgeCondition.ALWAYS);
        return e;
    }

    private WorkflowDefinition chainOf(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setType(WorkflowType.CHAIN);
        def.setNodes(nodes);
        def.setEdges(edges);
        return def;
    }

    // ── valid graph ───────────────────────────────────────────────────────────

    @Test
    void validFourNodeChainResolvesInOrder() throws InvalidWorkflowException {
        WorkflowNode start = node("start", NodeType.START);
        WorkflowNode a = node("a", NodeType.WORKER);
        WorkflowNode b = node("b", NodeType.WORKER);
        WorkflowNode end = node("end", NodeType.END);

        WorkflowDefinition def =
                chainOf(
                        Arrays.asList(start, a, b, end),
                        Arrays.asList(edge("start", "a"), edge("a", "b"), edge("b", "end")));

        ExecutionPlan plan = resolver.resolve(def);

        assertThat(plan.getOrderedNodes()).containsExactly(start, a, b, end);
    }

    @Test
    void resolvedPlanNodeCountMatchesInputNodeCount() throws InvalidWorkflowException {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode w = node("w", NodeType.WORKER);
        WorkflowNode end = node("e", NodeType.END);

        WorkflowDefinition def =
                chainOf(
                        Arrays.asList(start, w, end),
                        Arrays.asList(edge("s", "w"), edge("w", "e")));

        ExecutionPlan plan = resolver.resolve(def);

        assertThat(plan.getOrderedNodes()).hasSize(3);
    }

    @Test
    void resolvedPlanContainsCorrectOutgoingEdges() throws InvalidWorkflowException {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode end = node("e", NodeType.END);
        WorkflowEdge e1 = edge("s", "e");

        WorkflowDefinition def = chainOf(Arrays.asList(start, end), Collections.singletonList(e1));

        ExecutionPlan plan = resolver.resolve(def);

        assertThat(plan.getOutgoingEdges("s")).containsExactly(e1);
        assertThat(plan.getOutgoingEdges("e")).isEmpty();
    }

    // ── null / type guards ────────────────────────────────────────────────────

    @Test
    void resolveNullThrowsWithNonBlankMessage() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(InvalidWorkflowException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    void supervisorTypeThrows() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setType(WorkflowType.SUPERVISOR);
        def.setNodes(Collections.singletonList(node("s", NodeType.START)));
        def.setEdges(Collections.emptyList());

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class);
    }

    // ── structural validation ─────────────────────────────────────────────────

    @Test
    void missingStartNodeThrows() {
        WorkflowNode w = node("w", NodeType.WORKER);
        WorkflowNode end = node("e", NodeType.END);

        WorkflowDefinition def =
                chainOf(Arrays.asList(w, end), Collections.singletonList(edge("w", "e")));

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class);
    }

    @Test
    void missingEndNodeThrows() {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode w = node("w", NodeType.WORKER);

        WorkflowDefinition def =
                chainOf(Arrays.asList(start, w), Collections.singletonList(edge("s", "w")));

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class);
    }

    @Test
    void cycleThrowsWithCycleInMessage() {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode a = node("a", NodeType.WORKER);
        WorkflowNode b = node("b", NodeType.WORKER);
        WorkflowNode end = node("e", NodeType.END);

        // s→a, a→b, b→a (cycle), b→e — note: b has outgoing edges, a has incoming
        WorkflowDefinition def =
                chainOf(
                        Arrays.asList(start, a, b, end),
                        Arrays.asList(
                                edge("s", "a"), edge("a", "b"), edge("b", "a"), edge("b", "e")));

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void nonEndNodeWithNoOutgoingEdgeThrows() {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode w = node("w", NodeType.WORKER); // no outgoing edge
        WorkflowNode end = node("e", NodeType.END);

        WorkflowDefinition def =
                chainOf(
                        Arrays.asList(start, w, end),
                        Collections.singletonList(edge("s", "e")) // w is disconnected at output
                        );

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class);
    }

    @Test
    void nonStartNodeWithNoIncomingEdgeThrows() {
        WorkflowNode start = node("s", NodeType.START);
        WorkflowNode w = node("w", NodeType.WORKER); // no incoming edge
        WorkflowNode end = node("e", NodeType.END);

        WorkflowDefinition def =
                chainOf(
                        Arrays.asList(start, w, end),
                        Arrays.asList(edge("s", "e"), edge("w", "e")) // w has no incoming
                        );

        assertThatThrownBy(() -> resolver.resolve(def))
                .isInstanceOf(InvalidWorkflowException.class);
    }
}
