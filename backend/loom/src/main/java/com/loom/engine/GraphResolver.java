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

import com.loom.domain.NodeType;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.domain.WorkflowType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Validates a {@link WorkflowDefinition} and produces a topologically-ordered {@link ExecutionPlan}
 * ready for execution by the {@link WorkflowEngine}.
 *
 * <p>Validation includes: null/blank node IDs, duplicate node IDs, null/unknown edge endpoints,
 * exactly one START and one END node, connectivity of all nodes, and cycle detection via Kahn's
 * algorithm.
 */
public class GraphResolver {

    /**
     * Validates the workflow definition and returns an ExecutionPlan with nodes in topological
     * (execution) order.
     *
     * @param workflow the workflow definition to validate and resolve
     * @throws InvalidWorkflowException if the graph is null, not CHAIN type, contains null/blank or
     *     duplicate node IDs, references unknown edge endpoints, is missing START or END, has
     *     disconnected nodes, or contains a cycle.
     */
    public ExecutionPlan resolve(WorkflowDefinition workflow) throws InvalidWorkflowException {
        // 1. Null guard
        if (workflow == null) {
            throw new InvalidWorkflowException("workflow must not be null");
        }

        // 2. Type guard
        if (workflow.getType() != WorkflowType.CHAIN) {
            throw new InvalidWorkflowException("only CHAIN workflows are supported");
        }

        // 3. Node list guard
        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new InvalidWorkflowException("workflow has no nodes");
        }

        List<WorkflowEdge> edges =
                workflow.getEdges() != null ? workflow.getEdges() : Collections.emptyList();

        // 4. Build nodeById — validate each node before inserting
        Map<String, WorkflowNode> nodeById = new HashMap<>();
        for (WorkflowNode node : nodes) {
            if (node == null) {
                throw new InvalidWorkflowException("workflow contains a null node");
            }
            String id = node.getId();
            if (id == null || id.isBlank()) {
                throw new InvalidWorkflowException(
                        "workflow contains a node with a null or blank id");
            }
            if (nodeById.containsKey(id)) {
                throw new InvalidWorkflowException("duplicate node id: '" + id + "'");
            }
            nodeById.put(id, node);
        }

        // 5. Build edgesByFromNode and 6. incomingCount — validate each edge before inserting
        Map<String, List<WorkflowEdge>> edgesByFromNode = new HashMap<>();
        Map<String, Integer> incomingCount = new HashMap<>();
        for (WorkflowNode node : nodes) {
            edgesByFromNode.put(node.getId(), new ArrayList<>());
            incomingCount.put(node.getId(), 0);
        }
        for (WorkflowEdge edge : edges) {
            if (edge == null) {
                throw new InvalidWorkflowException("workflow contains a null edge");
            }
            String fromId = edge.getFromNodeId();
            String toId = edge.getToNodeId();
            if (!nodeById.containsKey(fromId)) {
                throw new InvalidWorkflowException(
                        "edge references unknown fromNodeId: '" + fromId + "'");
            }
            if (!nodeById.containsKey(toId)) {
                throw new InvalidWorkflowException(
                        "edge references unknown toNodeId: '" + toId + "'");
            }
            edgesByFromNode.get(fromId).add(edge);
            incomingCount.merge(toId, 1, Integer::sum);
        }

        // 7. Validate exactly one START node
        long startCount = nodes.stream().filter(n -> n.getNodeType() == NodeType.START).count();
        if (startCount == 0) {
            throw new InvalidWorkflowException("workflow must have exactly one START node");
        }
        if (startCount > 1) {
            throw new InvalidWorkflowException(
                    "workflow must have exactly one START node, found " + startCount);
        }

        // 8. Validate exactly one END node
        long endCount = nodes.stream().filter(n -> n.getNodeType() == NodeType.END).count();
        if (endCount == 0) {
            throw new InvalidWorkflowException("workflow must have exactly one END node");
        }
        if (endCount > 1) {
            throw new InvalidWorkflowException(
                    "workflow must have exactly one END node, found " + endCount);
        }

        // 9. Every non-END node must have ≥ 1 outgoing edge
        for (WorkflowNode node : nodes) {
            if (node.getNodeType() != NodeType.END && edgesByFromNode.get(node.getId()).isEmpty()) {
                throw new InvalidWorkflowException(
                        "node '" + node.getId() + "' has no outgoing edges but is not an END node");
            }
        }

        // 10. Every non-START node must have ≥ 1 incoming edge
        for (WorkflowNode node : nodes) {
            if (node.getNodeType() != NodeType.START && incomingCount.get(node.getId()) == 0) {
                throw new InvalidWorkflowException(
                        "node '"
                                + node.getId()
                                + "' has no incoming edges but is not a START node");
            }
        }

        // 11. Kahn's topological sort
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> remainingIn = new HashMap<>(incomingCount);
        for (Map.Entry<String, Integer> entry : remainingIn.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<WorkflowNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            sorted.add(nodeById.get(nodeId));
            for (WorkflowEdge edge : edgesByFromNode.get(nodeId)) {
                int newCount = remainingIn.merge(edge.getToNodeId(), -1, Integer::sum);
                if (newCount == 0) {
                    queue.add(edge.getToNodeId());
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new InvalidWorkflowException("cycle detected in workflow graph");
        }

        // 12. Build and return the ExecutionPlan
        return new ExecutionPlan(sorted, nodeById, edgesByFromNode);
    }
}
