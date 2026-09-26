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
package com.loom.engine.executor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowNode;
import com.loom.engine.StateManager;
import com.loom.engine.graph.ExecutionPlan;
import com.loom.engine.node.NodeResult;
import com.loom.engine.node.NodeRunner;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.storage.repository.SessionRepository;
import com.loom.transport.SSEManager;

@Slf4j
public class ChainExecutor {

    private final StateManager stateManager;
    private final NodeRunner nodeRunner;
    private final SessionRepository sessionRepository;
    private final SSEManager sseManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChainExecutor(
            StateManager stateManager,
            NodeRunner nodeRunner,
            SessionRepository sessionRepository,
            SSEManager sseManager) {
        this.stateManager = stateManager;
        this.nodeRunner = nodeRunner;
        this.sessionRepository = sessionRepository;
        this.sseManager = sseManager;
    }

    public void run(
            @NonNull Session session,
            @NonNull ExecutionPlan plan,
            @NonNull String inputContext,
            long timeoutSeconds) {
        String sessionId = session.getId();

        // Set status to running
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // Initialize all nodes as pending
        plan.getOrderedNodes()
                .forEach(
                        node ->
                                stateManager.setStatus(
                                        sessionId, node.getId(), AgentExecutionStatus.PENDING));

        // Broadcast session as started
        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        // Track context and activation
        Map<String, String> nodeContextMap = new HashMap<>();
        WorkflowNode startNode = plan.getOrderedNodes().get(0);
        nodeContextMap.put(startNode.getId(), inputContext);

        Set<String> activatedNodes = new HashSet<>();
        activatedNodes.add(startNode.getId());

        // Execute nodes in topological order
        for (WorkflowNode node : plan.getOrderedNodes()) {
            NodeType nodeType = node.getNodeType();
            final String nodeId = node.getId();

            if (NodeType.START.equals(nodeType)) {
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                List<WorkflowNode> nextNodes =
                        stateManager.resolveNextNodes(
                                sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                for (WorkflowNode nextNode : nextNodes) {
                    activatedNodes.add(nextNode.getId());
                    nodeContextMap.put(nextNode.getId(), inputContext);
                }
                propagateWaiting(sessionId, nodeId, plan, AgentExecutionStatus.COMPLETED);
                continue;
            }

            if (NodeType.END.equals(nodeType)) {
                if (!activatedNodes.contains(nodeId)) {
                    continue;
                }
                stateManager.setStatus(sessionId, nodeId, AgentExecutionStatus.COMPLETED);
                broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", nodeId));
                continue;
            }

            // WORKER node - skip if not activated
            if (!activatedNodes.contains(nodeId)) {
                continue;
            }

            String ctx = nodeContextMap.getOrDefault(nodeId, inputContext);

            // Delegate single-node execution to NodeRunner
            NodeResult result = nodeRunner.run(sessionId, node, ctx, timeoutSeconds);

            List<WorkflowNode> nextNodes =
                    stateManager.resolveNextNodes(sessionId, nodeId, plan, result.status());

            for (WorkflowNode nextNode : nextNodes) {
                activatedNodes.add(nextNode.getId());
                nodeContextMap.put(
                        nextNode.getId(),
                        result.isSuccess() && result.output() != null ? result.output() : ctx);
            }
            propagateWaiting(sessionId, nodeId, plan, result.status());
        }

        // Compute terminal status
        long completedCount =
                plan.getOrderedNodes().stream()
                        .filter(
                                node ->
                                        !NodeType.START.equals(node.getNodeType())
                                                && !NodeType.END.equals(node.getNodeType()))
                        .filter(
                                node ->
                                        AgentExecutionStatus.COMPLETED.equals(
                                                stateManager.getStatus(sessionId, node.getId())))
                        .count();

        long failedCount =
                plan.getOrderedNodes().stream()
                        .filter(
                                node ->
                                        !NodeType.START.equals(node.getNodeType())
                                                && !NodeType.END.equals(node.getNodeType()))
                        .filter(
                                node -> {
                                    AgentExecutionStatus status =
                                            stateManager.getStatus(sessionId, node.getId());
                                    return AgentExecutionStatus.FAILED.equals(status)
                                            || AgentExecutionStatus.TIMED_OUT.equals(status);
                                })
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
                (SessionStatus.COMPLETED.equals(terminalStatus))
                        ? EventType.SESSION_COMPLETED
                        : EventType.SESSION_FAILED;

        broadcast(sessionId, terminalEvent, Map.of());
    }

    private void propagateWaiting(
            String sessionId, String nodeId, ExecutionPlan plan, AgentExecutionStatus status) {
        List<WorkflowNode> waiting = stateManager.resolveNextNodes(sessionId, nodeId, plan, status);
        for (WorkflowNode w : waiting) {
            broadcast(sessionId, EventType.NODE_QUEUED, Map.of("nodeId", w.getId()));
        }
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
}
