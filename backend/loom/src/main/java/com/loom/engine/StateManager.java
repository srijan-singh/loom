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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.loom.domain.AgentExecution;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.EdgeCondition;
import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;
import com.loom.storage.repository.AgentExecutionRepository;

/**
 * Tracks the per-node execution status for all active workflow sessions.
 *
 * <p>Maintains an in-memory {@link ConcurrentHashMap} index keyed by {@code "sessionId:nodeId"} for
 * fast, thread-safe status lookup and atomic upsert, backed by an {@link AgentExecutionRepository}
 * for persistence.
 */
public class StateManager {

    private final AgentExecutionRepository executionRepository;

    /** In-memory index keyed by "sessionId:nodeId" for fast lookup and upsert. */
    private final ConcurrentHashMap<String, AgentExecution> index = new ConcurrentHashMap<>();

    /** Counts how many times setStatus(RUNNING) has been called per "sessionId:nodeId" key. */
    private final ConcurrentHashMap<String, AtomicInteger> iterationCounts =
            new ConcurrentHashMap<>();

    /**
     * Constructs a StateManager backed by the given repository.
     *
     * @param executionRepository repository used to persist agent execution records
     */
    public StateManager(AgentExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    /**
     * Creates or updates the AgentExecution record for (sessionId, nodeId) with the given status.
     * Sets startedAt if transitioning to RUNNING; sets completedAt if transitioning to COMPLETED or
     * FAILED.
     *
     * <p>For terminal transitions (COMPLETED / FAILED) the record is re-loaded from the repository
     * before the status update so that fields written by AgentRuntime — output, report,
     * inputContext, agentDefinitionId — are not overwritten by the sparse instance that was placed
     * in the in-memory index when status was first set to RUNNING. If the repository has no record
     * (e.g. in tests), the existing in-memory instance is reused.
     */
    public void setStatus(String sessionId, String nodeId, AgentExecutionStatus status) {
        String key = sessionId + ":" + nodeId;

        // For terminal transitions, re-fetch the enriched record that AgentRuntime
        // persisted (with output, report, inputContext, agentDefinitionId), then apply
        // only the terminal status and completedAt on top of it.  Fall back to the
        // existing in-memory object when the repository has no persisted record.
        if (status == AgentExecutionStatus.COMPLETED || status == AgentExecutionStatus.FAILED) {
            AgentExecution cached = index.get(key);
            AgentExecution fresh =
                    executionRepository.findBySessionIdAndNodeId(sessionId, nodeId).orElse(cached);
            AgentExecution target;
            if (fresh != null) {
                target = fresh;
            } else {
                target = new AgentExecution();
                target.setSessionId(sessionId);
                target.setNodeId(nodeId);
            }
            target.setStatus(status);
            target.setCompletedAt(System.currentTimeMillis());
            index.put(key, target);
            executionRepository.save(target);
            return;
        }

        AgentExecution exec =
                index.compute(
                        key,
                        (k, existing) -> {
                            AgentExecution e = existing != null ? existing : new AgentExecution();
                            if (existing == null) {
                                e.setSessionId(sessionId);
                                e.setNodeId(nodeId);
                            }
                            e.setStatus(status);
                            if (status == AgentExecutionStatus.RUNNING && e.getStartedAt() == 0) {
                                e.setStartedAt(System.currentTimeMillis());
                            }
                            return e;
                        });
        if (status == AgentExecutionStatus.RUNNING) {
            iterationCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        }
        executionRepository.save(exec);
    }

    /**
     * Returns the current status for (sessionId, nodeId). Returns AgentExecutionStatus.PENDING if
     * no record exists.
     */
    public AgentExecutionStatus getStatus(String sessionId, String nodeId) {
        String key = sessionId + ":" + nodeId;
        AgentExecution exec = index.get(key);
        if (exec == null) {
            return AgentExecutionStatus.PENDING;
        }
        AgentExecutionStatus status = exec.getStatus();
        return status != null ? status : AgentExecutionStatus.PENDING;
    }

    /**
     * Returns the number of times {@code setStatus(sessionId, supervisorNodeId, RUNNING)} has been
     * called. Used by {@code WorkflowEngine#runSupervisor} to enforce {@code maxIterations}.
     *
     * @param sessionId the session to query
     * @param supervisorNodeId the supervisor node id
     * @return iteration count, or {@code 0} if no RUNNING call has been made yet
     */
    public int getIterationCount(String sessionId, String supervisorNodeId) {
        String key = sessionId + ":" + supervisorNodeId;
        AtomicInteger counter = iterationCounts.get(key);
        return counter == null ? 0 : counter.get();
    }

    /**
     * Returns true when all nodes that have an outgoing edge to nodeId in the plan have status
     * COMPLETED. For the START node (no predecessors) returns true unconditionally.
     */
    public boolean areUpstreamsDone(String sessionId, String nodeId, ExecutionPlan plan) {
        List<WorkflowNode> predecessors = new ArrayList<>();
        for (WorkflowNode candidate : plan.getOrderedNodes()) {
            for (WorkflowEdge edge : plan.getOutgoingEdges(candidate.getId())) {
                if (nodeId.equals(edge.getToNodeId())) {
                    predecessors.add(candidate);
                    break;
                }
            }
        }
        if (predecessors.isEmpty()) {
            return true;
        }
        for (WorkflowNode predecessor : predecessors) {
            if (getStatus(sessionId, predecessor.getId()) != AgentExecutionStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the list of WorkflowNodes that should be activated next, based on the outgoing edges
     * from completedNodeId and the given status. - ON_SUCCESS → include only if status == COMPLETED
     * - ON_FAILURE → include only if status == FAILED - ALWAYS → include regardless of status
     */
    public List<WorkflowNode> resolveNextNodes(
            String sessionId,
            String completedNodeId,
            ExecutionPlan plan,
            AgentExecutionStatus status) {
        List<WorkflowNode> result = new ArrayList<>();
        for (WorkflowEdge edge : plan.getOutgoingEdges(completedNodeId)) {
            boolean include = false;
            EdgeCondition condition = edge.getCondition();
            if (condition == EdgeCondition.ON_SUCCESS) {
                include = (status == AgentExecutionStatus.COMPLETED);
            } else if (condition == EdgeCondition.ON_FAILURE) {
                include =
                        (status == AgentExecutionStatus.FAILED
                                || status == AgentExecutionStatus.TIMED_OUT);
            } else if (condition == EdgeCondition.ALWAYS) {
                include = true;
            }
            if (include) {
                result.add(plan.getNode(edge.getToNodeId()));
            }
        }
        return result;
    }
}
